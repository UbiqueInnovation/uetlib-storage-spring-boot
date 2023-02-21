package ch.ubique.backend.storage.clients;

import static ch.ubique.backend.storage.OutputMetaData.HTTP_DATE_FORMATTER;

import ch.ubique.backend.core.http.cache.CacheHeader;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.OutputMetadataProvider;
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
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
    @Nullable private final OutputMetadataProvider outputMetadataProvider;

    public MinioStorageClient(
            final String bucket,
            final String endpoint,
            final String accessKey,
            final String secretKey,
            final Duration timeout,
            final OutputMetadataProvider outputMetadataProvider) {
        this.bucket = bucket;
        this.outputMetadataProvider = outputMetadataProvider;
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
        final var metaData = resolveOutputMetaData(outputMetaData, outputMetadataProvider);

        try (final var inputStream = output.read()) {

            final var contentType = Objects.requireNonNull(output.contentType());

            final var putObjectArgs =
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .contentType(contentType)
                            .headers(createHeaders(output, metaData))
                            .stream(inputStream, inputStream.available(), -1);
            putObjectArgs.userMetadata(createMetaDataMap(metaData));
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

    public Map<String, String> createMetaDataMap(final OutputMetaData outputMetaData) {
        final var map = new HashMap<String, String>();

        final var nextRefresh = outputMetaData.getNextRefresh();
        if (nextRefresh != null) {
            map.put(CacheHeader.NEXT_REFRESH.value(), HTTP_DATE_FORMATTER.format(nextRefresh));
        }
        final var bestBefore = outputMetaData.getBestBefore();
        if (bestBefore != null) {
            map.put(CacheHeader.BEST_BEFORE.value(), HTTP_DATE_FORMATTER.format(bestBefore));
        }
        final var backoff = outputMetaData.getBackoff();
        if (backoff != null) {
            map.put(CacheHeader.REFRESH_BACKOFF.value(), backoff.toString());
        }

        return Collections.unmodifiableMap(map);
    }
}
