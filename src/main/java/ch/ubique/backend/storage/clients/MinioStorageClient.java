package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.Output;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import com.pivovarit.function.exception.WrappedException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.http.Method;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.apache.commons.lang3.NotImplementedException;

/**
 * {@link ch.ubique.backend.storage.StorageClient } to perform bucket and object operations to any
 * Amazon S3 compatible object storage service.
 */
public class MinioStorageClient implements StorageClient {

    private final String bucket;
    private final MinioClient minioClient;

    public MinioStorageClient(String bucket, String endpoint, String accessKey, String secretKey) {
        this.bucket = bucket;
        minioClient =
                MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @Override
    public void write(Output output, String path, @Nullable OutputMetaData metaData)
            throws IOException {
        try (final var inputStream = output.read()) {
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(path).stream(
                                    inputStream, inputStream.available(), -1)
                            .contentType(output.contentType())
                            .build());
        } catch (ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public InputStream read(String path) throws IOException {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(path).build());
        } catch (ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public boolean delete(String path) throws IOException {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(path).build());
        } catch (ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException e) {
            throw new WrappedException(e);
        }
        return true;
    }

    @Override
    public String getSignedUrl(String path, OffsetDateTime expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .expiry(
                                    (int)
                                            Duration.between(OffsetDateTime.now(), expiry)
                                                    .toSeconds())
                            .bucket(bucket)
                            .build());
        } catch (ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException
                | IOException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public String getUrl(String path) {
        throw new NotImplementedException(
                "Minio (actually S3) does not support permanent share URLs? (Maximum is 7 days as"
                        + " per S3 spec)");
    }
}
