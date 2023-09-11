package ch.ubique.backend.storage.clients;

import ch.ubique.backend.core.utils.StreamUtils;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.OutputMetaDataProvider;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.Output;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.Context;
import com.azure.core.util.Header;
import com.azure.core.util.HttpClientOptions;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.*;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.policy.RequestRetryOptions;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AzureBlobStorageClient implements StorageClient {

    private static final Logger logger = LoggerFactory.getLogger(AzureBlobStorageClient.class);

    private final BlobServiceClient blobServiceClient;
    private final BlobContainerClient blobContainerClient;
    @Nullable private final OutputMetaDataProvider outputMetaDataProvider;
    private final long blockSize;
    private final Duration timeout;

    public AzureBlobStorageClient(final String container, final String connectionString) {
        this(container, connectionString, null, null);
    }

    public AzureBlobStorageClient(
            final String container,
            final String connectionString,
            final Duration timeout,
            @Nullable final OutputMetaDataProvider outputMetaDataProvider) {
        this(container, connectionString, timeout, 1024L, outputMetaDataProvider);
    }

    public AzureBlobStorageClient(
            final String container,
            final String connectionString,
            final Duration timeout,
            final long blockSize,
            @Nullable final OutputMetaDataProvider outputMetaDataProvider) {
        this.timeout = Objects.requireNonNullElse(timeout, Duration.ofSeconds(60));
        final var httpClient =
                HttpClient.createDefault(
                        new HttpClientOptions()
                                .setConnectTimeout(this.timeout)
                                .setReadTimeout(this.timeout)
                                .setWriteTimeout(this.timeout));
        this.blobServiceClient =
                new BlobServiceClientBuilder()
                        .connectionString(connectionString)
                        .retryOptions(new RequestRetryOptions())
                        .httpClient(httpClient)
                        .buildClient();
        this.blobContainerClient = blobServiceClient.getBlobContainerClient(container);
        this.outputMetaDataProvider = outputMetaDataProvider;
        this.blockSize = blockSize;
    }

    @NotNull
    private static BlobHttpHeaders createHeaders(
            @NotNull final Output output, @Nullable final OutputMetaData metaData) {
        final var headers = new BlobHttpHeaders();

        // contentType
        headers.setContentType(Objects.requireNonNull(output.contentType()));

        // contentEncoding
        output.contentEncoding().ifPresent(headers::setContentEncoding);

        // cacheControl
        Optional.ofNullable(metaData)
                .map(OutputMetaData::createCacheControl)
                .ifPresent(headers::setCacheControl);

        return headers;
    }

    @Override
    public void write(
            final @NotNull Output output,
            final @NotNull String path,
            @Nullable final OutputMetaData outputMetaData)
            throws IOException {
        final var metaData =
                OutputMetaData.resolveOutputMetaData(
                        outputMetaData, outputMetaDataProvider, output);

        try (final var inputStream = output.read()) {

            // Define upload
            final var uploadOptions = new BlobParallelUploadOptions(inputStream);
            uploadOptions.setHeaders(createHeaders(output, metaData));
            uploadOptions.setMetadata(metaData.createAzureMetaDataMap());
            uploadOptions.setRequestConditions(new BlobRequestConditions());
            uploadOptions.setParallelTransferOptions(
                    new ParallelTransferOptions().setMaxConcurrency(4).setBlockSizeLong(blockSize));

            // Upload
            blobContainerClient
                    .getBlobClient(path)
                    .uploadWithResponse(uploadOptions, null, Context.NONE);
        }
    }

    @Override
    public InputStream read(final @NotNull String path, final boolean doUncompress)
            throws IOException {
        try (final var outputStream = new ByteArrayOutputStream()) {
            final var blobDownloadResponse = downloadStreamWithResponse(path, outputStream);
            final var inputStream = StreamUtils.pipeOutputToInputStream(outputStream);

            if (doUncompress) {
                final var contentEncoding =
                        Optional.ofNullable(
                                        blobDownloadResponse
                                                .getHeaders()
                                                .get(HttpHeaderName.CONTENT_ENCODING))
                                .map(Header::getValue)
                                .orElse(null);
                logger.debug("uncompressing input from content-encoding {}", contentEncoding);
                return doUncompress(contentEncoding, inputStream);
            }

            return inputStream;
        }
    }

    private BlobDownloadResponse downloadStreamWithResponse(
            @NotNull final String path, final ByteArrayOutputStream outputStream) {
        return blobContainerClient
                .getBlobClient(path)
                .downloadStreamWithResponse(
                        outputStream, null, null, null, false, null, Context.NONE);
    }

    @Override
    public boolean delete(final @NotNull String path, final boolean recursive) throws IOException {
        final var existing = list(path, true);
        if (existing.stream().anyMatch(object -> !object.equals(path)) && !recursive) {
            throw new DirectoryNotEmptyException(path);
        }

        if (recursive) {
            for (final var object : existing) {
                delete(object);
            }
        }
        return blobContainerClient.getBlobClient(path).deleteIfExists();
    }

    @Override
    public Collection<String> list(@NotNull final String path, final boolean recursive)
            throws IOException {

        final PagedIterable<BlobItem> blobItems;
        if (recursive) {
            final var options = new ListBlobsOptions().setPrefix(path);
            blobItems = blobContainerClient.listBlobs(options, timeout);
        } else {
            blobItems = blobContainerClient.listBlobsByHierarchy(path);
        }
        return blobItems.stream().map(BlobItem::getName).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getSignedUrl(final @NotNull String path, final @NotNull OffsetDateTime expiry) {
        final var blobSasPermission = new BlobSasPermission().setReadPermission(true);
        final var values =
                new BlobServiceSasSignatureValues(expiry, blobSasPermission)
                        .setStartTime(OffsetDateTime.now());

        final var blobUrlEncoded = URLEncoder.encode(path, StandardCharsets.UTF_8);
        final var blobClient = blobContainerClient.getBlobClient(blobUrlEncoded);

        return URLDecoder.decode(blobClient.getBlobUrl(), StandardCharsets.UTF_8)
                + "?"
                + blobClient.generateSas(values);
    }

    @Override
    public String getUrl(final @NotNull String path) {
        final var blobUrlEncoded = URLEncoder.encode(path, StandardCharsets.UTF_8);
        final var blobClient = blobContainerClient.getBlobClient(blobUrlEncoded);
        return URLDecoder.decode(blobClient.getBlobUrl(), StandardCharsets.UTF_8);
    }

    public void createContainer(final String containerName) {
        blobServiceClient.createBlobContainer(containerName);
    }
}
