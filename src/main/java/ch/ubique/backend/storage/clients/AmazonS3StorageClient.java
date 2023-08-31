package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.OutputMetaDataProvider;
import jakarta.annotation.Nullable;

import java.time.Duration;

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
            @Nullable final String endpoint,
            final String accessKey,
            final String secretKey) {
        this(bucket, endpoint, accessKey, secretKey, null, null);
    }

    /**
     * @param bucket The bucket name
     * @param endpoint The endpoint URL of the S3 service. If null, the default endpoint. E.g.,
     *     s3.eu-central-1.amazonaws.com
     * @param accessKey The AWS auth access key
     * @param secretKey The AWS auth secret key
     * @param timeout Timeout for requests
     * @param outputMetaDataProvider Provider for output meta data
     */
    public AmazonS3StorageClient(
            final String bucket,
            @Nullable final String endpoint,
            final String accessKey,
            final String secretKey,
            final Duration timeout,
            final OutputMetaDataProvider outputMetaDataProvider) {
        super(
                bucket,
                endpoint == null ? "s3.eu-central-1.amazonaws.com" : endpoint,
                accessKey,
                secretKey,
                timeout,
                outputMetaDataProvider);
    }
}
