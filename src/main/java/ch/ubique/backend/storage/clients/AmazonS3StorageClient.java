package ch.ubique.backend.storage.clients;

import java.time.Duration;

public final class AmazonS3StorageClient extends MinioStorageClient {

    public AmazonS3StorageClient(
            final String bucket, final String accessKey, final String secretKey) {
        this(bucket, accessKey, secretKey, null);
    }

    public AmazonS3StorageClient(
            final String bucket,
            final String accessKey,
            final String secretKey,
            final Duration timeout) {
        super(bucket, "https://s3.amazonaws.com", accessKey, secretKey, timeout);
    }
}
