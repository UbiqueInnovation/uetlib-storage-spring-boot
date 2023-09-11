package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.OutputMetaDataProvider;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.Output;

import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingFunction;
import com.pivovarit.function.exception.WrappedException;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.http.Method;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import okhttp3.OkHttpClient;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * {@link StorageClient} to perform bucket and object operations to any Amazon S3 / MinIO compatible
 * object storage service.
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
            @Nullable final Duration timeout,
            @Nullable final OutputMetaDataProvider outputMetaDataProvider) {
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
                .ifPresent(
                        contentEncoding ->
                                headers.put(HttpHeaders.CONTENT_ENCODING, contentEncoding));

        // cacheControl
        Optional.ofNullable(metaData)
                .map(OutputMetaData::createCacheControl)
                .ifPresent(cacheControl -> headers.put(HttpHeaders.CACHE_CONTROL, cacheControl));

        return headers;
    }

    @Override
    public final void write(
            final @NotNull Output output,
            final @NotNull String path,
            @Nullable final OutputMetaData outputMetaData)
            throws IOException {
        final var metaData =
                OutputMetaData.resolveOutputMetaData(
                        outputMetaData, outputMetaDataProvider, output);

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
    public final InputStream read(final @NotNull String path) throws IOException {
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
    public final boolean delete(final @NotNull String path, final boolean recursive)
            throws IOException {

        final var existing = list(path, true);

        if (existing.stream().anyMatch(object -> !object.equals(path)) && !recursive) {
            throw new DirectoryNotEmptyException(path);
        }

        final var objectsToDelete =
                recursive
                        ? existing.stream().map(DeleteObject::new).toList()
                        : List.of(new DeleteObject(path));

        minioClient
                .removeObjects(
                        RemoveObjectsArgs.builder().bucket(bucket).objects(objectsToDelete).build())
                .forEach(ThrowingConsumer.unchecked(Result::get));

        return true;
    }

    @Override
    public Collection<String> list(@NotNull final String path, final boolean recursive)
            throws IOException {
        final var objects =
                minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(bucket)
                                .prefix(path)
                                .recursive(recursive)
                                .build());

        return StreamSupport.stream(objects.spliterator(), false)
                .map(ThrowingFunction.unchecked(Result::get))
                .map(Item::objectName)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public final String getSignedUrl(
            final @NotNull String path, final @NotNull OffsetDateTime expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .expiry(
                                    (int)
                                            Duration.between(OffsetDateTime.now(), expiry)
                                                    .toSeconds())
                            .bucket(bucket)
                            .object(path)
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
    public final String getUrl(final @NotNull String path) {
        throw new NotImplementedException(
                "Minio (actually S3) does not support permanent share URLs? (Maximum is 7 days as"
                        + " per S3 spec)");
    }

    public void createBucket(final String bucketName) {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        } catch (final ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | IOException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException e) {
            throw new RuntimeException(e);
        }
    }
}
