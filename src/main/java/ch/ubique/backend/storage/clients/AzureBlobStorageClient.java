package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.OutputMetaDataProvider;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.Output;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.policy.RequestRetryOptions;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

public class AzureBlobStorageClient implements StorageClient {

    private final String container;
    private final BlobServiceClient blobServiceClient;
    @Nullable private final OutputMetaDataProvider outputMetaDataProvider;

    public AzureBlobStorageClient(final String container, final String connectionString) {
        this(container, connectionString, null);
    }

    public AzureBlobStorageClient(
            final String container,
            final String connectionString,
            final OutputMetaDataProvider outputMetaDataProvider) {
        this.container = container;
        this.blobServiceClient =
                new BlobServiceClientBuilder()
                        .connectionString(connectionString)
                        .retryOptions(new RequestRetryOptions())
                        .buildClient();
        this.outputMetaDataProvider = outputMetaDataProvider;
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
            final Output output, final String path, @Nullable final OutputMetaData outputMetaData)
            throws IOException {
        final var metaData = resolveOutputMetaData(outputMetaData, outputMetaDataProvider);

        try (final var inputStream = output.read()) {

            // Define upload
            final var uploadOptions = new BlobParallelUploadOptions(inputStream);
            uploadOptions.setHeaders(createHeaders(output, metaData));
            Optional.ofNullable(metaData)
                    .map(OutputMetaData::createAzureMetaDataMap)
                    .ifPresent(uploadOptions::setMetadata);
            uploadOptions.setRequestConditions(new BlobRequestConditions());
            uploadOptions.setParallelTransferOptions(
                    new ParallelTransferOptions().setMaxConcurrency(100));

            // Upload
            final var timeout = Duration.ofSeconds(60);
            blobServiceClient
                    .getBlobContainerClient(container)
                    .getBlobClient(path)
                    .uploadWithResponse(uploadOptions, timeout, Context.NONE);
        }
    }

    @Override
    public InputStream read(final String path) throws IOException {
        final byte[] data;
        try (final var outputStream = new ByteArrayOutputStream()) {
            blobServiceClient
                    .getBlobContainerClient(container)
                    .getBlobClient(path)
                    .downloadStream(outputStream);
            data = outputStream.toByteArray();
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public boolean delete(final String path) {
        return blobServiceClient
                .getBlobContainerClient(container)
                .getBlobClient(path)
                .deleteIfExists();
    }

    @Override
    public String getSignedUrl(final String path, final OffsetDateTime expiry) {
        final var blobSasPermission = new BlobSasPermission().setReadPermission(true);
        final var values =
                new BlobServiceSasSignatureValues(expiry, blobSasPermission)
                        .setStartTime(OffsetDateTime.now());

        final var blobUrlEncoded = URLEncoder.encode(path, StandardCharsets.UTF_8);
        final var blobClient =
                blobServiceClient.getBlobContainerClient(container).getBlobClient(blobUrlEncoded);

        return URLDecoder.decode(blobClient.getBlobUrl(), StandardCharsets.UTF_8)
                + "?"
                + blobClient.generateSas(values);
    }

    @Override
    public String getUrl(final String path) {
        final var blobUrlEncoded = URLEncoder.encode(path, StandardCharsets.UTF_8);
        final var blobClient =
                blobServiceClient.getBlobContainerClient(container).getBlobClient(blobUrlEncoded);
        return URLDecoder.decode(blobClient.getBlobUrl(), StandardCharsets.UTF_8);
    }
}
