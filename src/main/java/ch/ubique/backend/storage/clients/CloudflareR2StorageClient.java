package ch.ubique.backend.storage.clients;

public class CloudflareR2StorageClient extends MinioStorageClient {
    public CloudflareR2StorageClient(
            String bucket, String accountId, String accessKey, String secretKey) {
        super(bucket, "https://" + accountId + ".r2.cloudflarestorage.com", accessKey, secretKey);
    }
}
