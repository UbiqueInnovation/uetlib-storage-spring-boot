package ch.ubique.backend.storage.clients;

import ch.ubique.backend.core.http.cache.CacheHeaderUtility;
import ch.ubique.backend.storage.Output;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AzureBlobStorageClient implements StorageClient {

    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                    .withZone(ZoneId.of("UTC"));

    private final String container;
    private final BlobServiceClient blobServiceClient;

    public AzureBlobStorageClient(final String container, final String connectionString) {
        this.container = container;
        this.blobServiceClient =
                new BlobServiceClientBuilder()
                        .connectionString(connectionString)
                        .retryOptions(new RequestRetryOptions())
                        .buildClient();
    }

    @Override
    public void write(Output output, String path, OutputMetaData metaData) throws IOException {
        try (final var inputStream = output.read()) {

            // Set important headers
            final var headers = new BlobHttpHeaders();
            headers.setContentType(output.contentType());
            output.contentEncoding().ifPresent(headers::setContentEncoding);
            metaData.getCacheControl().ifPresent(headers::setCacheControl);

            // Define upload
            final var uploadOptions = new BlobParallelUploadOptions(inputStream);
            uploadOptions.setHeaders(headers);
            uploadOptions.setMetadata(createAzureMetaData(metaData));
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
    public InputStream read(String path) throws IOException {
        byte[] data;
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
    public boolean delete(String path) {
        return blobServiceClient
                .getBlobContainerClient(container)
                .getBlobClient(path)
                .deleteIfExists();
    }

    @Override
    public String getSignedUrl(String path, OffsetDateTime expiry) {
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

    /** Creates the azure metadata object based on the given {@link OutputMetaData}. */
    private HashMap<String, String> createAzureMetaData(OutputMetaData metaData) {
        final var azureMetaData = new HashMap<String, String>();
        if (metaData.getNextRefresh() != null) {
            azureMetaData.put(
                    CacheHeaderUtility.NEXT_REFRESH_SHORT,
                    HTTP_DATE_FORMATTER.format(metaData.getNextRefresh()));
        }
        if (metaData.getBestBefore() != null) {
            azureMetaData.put(
                    CacheHeaderUtility.BEST_BEFORE_SHORT,
                    HTTP_DATE_FORMATTER.format(metaData.getBestBefore()));
        }
        if (metaData.getCache() != null) {
            azureMetaData.put(CacheHeaderUtility.CACHE_SHORT, metaData.getCache());
        }
        if (metaData.getBackoff() != null) {
            azureMetaData.put(
                    CacheHeaderUtility.REFRESH_BACKOFF_SHORT, metaData.getBackoff().toString());
        }
        azureMetaData.put(
                CacheHeaderUtility.MINIMUM_API_VERSION_SHORT,
                Optional.ofNullable(metaData.getMinimumApiVersion())
                        .orElse(CacheHeaderUtility.DEFAULT_API_VERSION)
                        .toString());

        return azureMetaData;
    }
}
