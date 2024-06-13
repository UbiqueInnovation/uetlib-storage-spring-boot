package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.OutputMetaDataProvider;

import jakarta.annotation.Nullable;

import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;

public final class CloudflareR2StorageClient extends RetryStorageClient {

    public CloudflareR2StorageClient(
            final String bucket,
            final String accountId,
            final String accessKey,
            final String secretKey) {
        this(bucket, accountId, accessKey, secretKey, null, null);
    }

    public CloudflareR2StorageClient(
            final String bucket,
            final String accountId,
            final String accessKey,
            final String secretKey,
            final Duration timeout,
            final OutputMetaDataProvider outputMetaDataProvider) {
        this(bucket, accountId, accessKey, secretKey, timeout, outputMetaDataProvider, null);
    }

    public CloudflareR2StorageClient(
            final String bucket,
            final String accountId,
            final String accessKey,
            final String secretKey,
            final Duration timeout,
            final OutputMetaDataProvider outputMetaDataProvider,
            @Nullable final RetryTemplate retryTemplate) {
        super(
                new MinioStorageClient(
                        bucket,
                        "https://" + accountId + ".r2.cloudflarestorage.com",
                        accessKey,
                        secretKey,
                        false,
                        timeout,
                        outputMetaDataProvider),
                retryTemplate);
    }
}
