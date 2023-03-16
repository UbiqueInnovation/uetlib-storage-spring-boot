package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.OutputMetaDataProvider;
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
import jakarta.validation.constraints.NotNull;

import okhttp3.OkHttpClient;

import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link ch.ubique.backend.storage.StorageClient } to perform bucket and object operations to any
 * Amazon S3 compatible object storage service.
 */
public sealed class MinioStorageClient implements StorageClient
        permits AmazonS3StorageClient, CloudflareR2StorageClient {

    private final String bucket;
    private final MinioClient minioClient;
    @Nullable private final OutputMetaDataProvider outputMetaDataProvider;

    public MinioStorageClient(
            final String bucket,
            final String endpoint,
            final String accessKey,
            final String secretKey,
            final Duration timeout,
            final OutputMetaDataProvider outputMetaDataProvider) {
        this.bucket = bucket;
        this.outputMetaDataProvider = outputMetaDataProvider;
        final var timeoutOrDefault = Objects.requireNonNullElse(timeout, Duration.ofSeconds(15));
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

    @NotNull
    private static Map<String, String> createHeaders(
            @NotNull final Output output, @Nullable final OutputMetaData metaData) {
        final var headers = new HashMap<String, String>();

        // contentEncoding
        output.contentEncoding()
                .ifPresent(contentEncoding -> headers.put("Content-Encoding", contentEncoding));

        // cacheControl
        Optional.ofNullable(metaData)
                .map(OutputMetaData::createCacheControl)
                .ifPresent(cacheControl -> headers.put("Cache-Control", cacheControl));

        return headers;
    }

    @Override
    public final void write(
            final Output output, final String path, @Nullable final OutputMetaData outputMetaData)
            throws IOException {
        final var metaData = resolveOutputMetaData(outputMetaData, outputMetaDataProvider);

        try (final var inputStream = output.read()) {

            final var contentType = Objects.requireNonNull(output.contentType());

            final var putObjectArgs =
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .contentType(contentType)
                            .headers(createHeaders(output, metaData))
                            .stream(inputStream, inputStream.available(), -1);
            putObjectArgs.userMetadata(metaData.createMinioMetaDataMap());
            minioClient.putObject(putObjectArgs.build());
        } catch (final ErrorResponseException
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
    public final InputStream read(final String path) throws IOException {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(path).build());
        } catch (final ErrorResponseException
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
    public final boolean delete(final String path) throws IOException {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(path).build());
        } catch (final ErrorResponseException
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
    public final String getSignedUrl(final String path, final OffsetDateTime expiry) {
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
        } catch (final ErrorResponseException
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
    public final String getUrl(final String path) {
        throw new NotImplementedException(
                "Minio (actually S3) does not support permanent share URLs? (Maximum is 7 days as"
                        + " per S3 spec)");
    }
}
