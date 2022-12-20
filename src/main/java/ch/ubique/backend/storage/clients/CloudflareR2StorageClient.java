package ch.ubique.backend.storage.clients;

import java.time.Duration;

public final class CloudflareR2StorageClient extends MinioStorageClient {

    public CloudflareR2StorageClient(
            final String bucket,
            final String accountId,
            final String accessKey,
            final String secretKey) {
        this(bucket, accountId, accessKey, secretKey, null);
    }

    public CloudflareR2StorageClient(
            final String bucket,
            final String accountId,
            final String accessKey,
            final String secretKey,
            final Duration timeout) {
        super(
                bucket,
                "https://" + accountId + ".r2.cloudflarestorage.com",
                accessKey,
                secretKey,
                timeout);
    }
}
