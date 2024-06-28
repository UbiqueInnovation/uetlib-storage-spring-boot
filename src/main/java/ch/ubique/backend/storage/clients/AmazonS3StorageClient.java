package ch.ubique.backend.storage.clients;

import ch.ubique.backend.core.http.header.HttpHeader;
import ch.ubique.backend.storage.OutputMetaDataProvider;

import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Objects;

public final class AmazonS3StorageClient extends MinioStorageClient {

    /**
     * @see #AmazonS3StorageClient(String, String, String, String, Duration, OutputMetaDataProvider)
     */
    public AmazonS3StorageClient(
            final String bucket, final String accessKey, final String secretKey) {
        this(bucket, null, accessKey, secretKey, null, null);
    }

    /**
     * @see #AmazonS3StorageClient(String, String, String, String, Duration, OutputMetaDataProvider)
     */
    public AmazonS3StorageClient(
            final String bucket,
            @Nullable final String region,
            final String accessKey,
            final String secretKey) {
        this(bucket, region, accessKey, secretKey, null, null);
    }

    /**
     * @param bucket The bucket name
     * @param region The endpoint URL of the S3 service. If null, the default endpoint, eu-central-1
     * @param accessKey The AWS auth access key
     * @param secretKey The AWS auth secret key
     * @param timeout Timeout for requests
     * @param outputMetaDataProvider Provider for output meta data
     */
    public AmazonS3StorageClient(
            final String bucket,
            @Nullable final String region,
            final String accessKey,
            final String secretKey,
            final Duration timeout,
            final OutputMetaDataProvider outputMetaDataProvider) {
        super(
                bucket,
                "s3." + Objects.requireNonNullElse(region, "eu-central-1") + ".amazonaws.com",
                accessKey,
                secretKey,
                true,
                timeout,
                outputMetaDataProvider);
    }

    @Override
    protected String keyForCustomMetadata(final HttpHeader httpHeader) {
        return httpHeader.withAmazonPrefix();
    }
}
