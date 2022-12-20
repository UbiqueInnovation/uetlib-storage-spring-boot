package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.Output;
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
import java.util.Map;
import java.util.Optional;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.NotImplementedException;

/**
 * {@link ch.ubique.backend.storage.StorageClient } to perform bucket and object operations to any
 * Amazon S3 compatible object storage service.
 */
public sealed class MinioStorageClient implements StorageClient
        permits AmazonS3StorageClient, CloudflareR2StorageClient {

    private final String bucket;
    private final MinioClient minioClient;

    public MinioStorageClient(
            final String bucket,
            final String endpoint,
            final String accessKey,
            final String secretKey,
            final Duration timeout) {
        this.bucket = bucket;
        final var timeoutOrDefault = Optional.ofNullable(timeout).orElse(Duration.ofSeconds(15));
        final var httpClient =
                new OkHttpClient()
                        .newBuilder()
                        .connectTimeout(timeoutOrDefault)
                        .readTimeout(timeoutOrDefault)
                        .writeTimeout(timeoutOrDefault)
                        .build();
        minioClient =
                MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials(accessKey, secretKey)
                        .httpClient(httpClient)
                        .build();
    }

    @Override
    public final void write(Output output, String path, @Nullable OutputMetaData metaData)
            throws IOException {

        final var headers =
                output.isGZip() ? Map.of("Content-Encoding", "gzip") : Map.<String, String>of();

        try (final var inputStream = output.read()) {
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(path).stream(
                                    inputStream, inputStream.available(), -1)
                            .contentType(output.contentType())
                            .headers(headers)
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
    public final InputStream read(String path) throws IOException {
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
    public final boolean delete(String path) throws IOException {
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
    public final String getSignedUrl(String path, OffsetDateTime expiry) {
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
    public final String getUrl(String path) {
        throw new NotImplementedException(
                "Minio (actually S3) does not support permanent share URLs? (Maximum is 7 days as"
                        + " per S3 spec)");
    }
}
