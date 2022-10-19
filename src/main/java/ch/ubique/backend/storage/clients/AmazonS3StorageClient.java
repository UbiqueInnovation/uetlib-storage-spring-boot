package ch.ubique.backend.storage.clients;

public class AmazonS3StorageClient extends MinioStorageClient {

    public AmazonS3StorageClient(String bucket, String accessKey, String secretKey) {
        super(bucket, "https://s3.amazonaws.com", accessKey, secretKey);
    }
}
