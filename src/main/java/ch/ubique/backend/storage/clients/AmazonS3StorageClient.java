package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.OutputMetaDataProvider;

import java.time.Duration;

public final class AmazonS3StorageClient extends MinioStorageClient {

    public AmazonS3StorageClient(
            final String bucket, final String accessKey, final String secretKey) {
        this(bucket, accessKey, secretKey, null, null);
    }

    public AmazonS3StorageClient(
            final String bucket,
            final String accessKey,
            final String secretKey,
            final Duration timeout,
            final OutputMetaDataProvider outputMetaDataProvider) {
        super(
                bucket,
                "https://s3.amazonaws.com",
                accessKey,
                secretKey,
                timeout,
                outputMetaDataProvider);
    }
}
