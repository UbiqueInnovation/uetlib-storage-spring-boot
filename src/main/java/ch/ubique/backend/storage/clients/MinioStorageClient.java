package ch.ubique.backend.storage.clients;

import ch.ubique.backend.core.executor.VirtualThreadUtils;
import ch.ubique.backend.core.http.header.HttpHeader;
import ch.ubique.backend.core.utils.InputStreamUtils;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.OutputMetaDataProvider;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.exception.StorageClientException;
import ch.ubique.backend.storage.output.Output;
import ch.ubique.backend.storage.output.OutputWithPath;

import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingFunction;

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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import okhttp3.OkHttpClient;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * {@link StorageClient} to perform bucket and object operations to any Amazon S3 / MinIO compatible
 * object storage service.
 */
public sealed class MinioStorageClient implements StorageClient permits AmazonS3StorageClient {

    private final String bucket;
    private final MinioClient minioClient;
    private final boolean supportsSnowball;
    @Nullable private final OutputMetaDataProvider outputMetaDataProvider;

    public MinioStorageClient(
            final String bucket,
            final String endpoint,
            final String accessKey,
            final String secretKey,
            final boolean supportsSnowball,
            @Nullable final Duration timeout,
            @Nullable final OutputMetaDataProvider outputMetaDataProvider) {
        this.bucket = bucket;
        this.supportsSnowball = supportsSnowball;
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

        // Expires
        Optional.ofNullable(metaData)
                .flatMap(OutputMetaData::expires)
                .ifPresent(expires -> headers.put(HttpHeaders.EXPIRES, expires));

        // ACL (Access Control)
        Optional.ofNullable(metaData)
                .flatMap(OutputMetaData::createCannedAcl)
                .ifPresent(cannedAcl -> headers.put(HttpHeader.AMZ_ACL.value(), cannedAcl));

        return headers;
    }

    private static @NotNull List<SnowballObject> createSnowballObjects(
            final Collection<@NotNull @Valid OutputWithPath> outputsWithPaths) throws IOException {
        try {
            return outputsWithPaths.stream()
                    .map(
                            outputWithPath -> {
                                try {
                                    return createSnowBallObject(outputWithPath);
                                } catch (final IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            })
                    .toList();
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static @NotNull SnowballObject createSnowBallObject(
            final @NotNull OutputWithPath outputWithPath) throws IOException {
        final var bytes = InputStreamUtils.readBytesAndClose(outputWithPath.output().read());
        final var inputStream = new ByteArrayInputStream(bytes);
        return new SnowballObject(
                outputWithPath.path(), inputStream, bytes.length, ZonedDateTime.now());
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

            // 8MiB (default in aws s3 cli, must be between 5MiB to 5GiB)
            // CAUTION: The upload is restricted to 10000 * partSize = 80GiB at the moment. We might
            // have to increase this at some point for unknown contentLength
            final var fallbackPartSize = 8 * 1024 * 1024;
            final var partSize = output.contentLength() > 0 ? -1 : fallbackPartSize;

            final var putObjectArgs =
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .contentType(contentType)
                            .headers(createHeaders(output, metaData))
                            .stream(inputStream, output.contentLength(), partSize)
                            .userMetadata(createUserMetadata(metaData))
                            .build();
            minioClient.putObject(putObjectArgs);
        } catch (final ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException e) {
            throw new StorageClientException(
                    MessageFormat.format(
                            "failed to write for [path={0}, outputMetaData={1}, output={2}]",
                            path, outputMetaData, output),
                    e);
        }
    }

    private Map<String, String> createUserMetadata(final OutputMetaData outputMetaData) {
        return outputMetaData.createCustomHeadersWithKeyMapper(this::keyForCustomMetadata);
    }

    protected String keyForCustomMetadata(final HttpHeader httpHeader) {
        return httpHeader.withXPrefix();
    }

    @Override
    public final void write(
            final Collection<@NotNull @Valid OutputWithPath> outputsWithPaths,
            @Nullable final OutputMetaData outputMetaData)
            throws IOException {

        if (outputsWithPaths.isEmpty()) {
            return;
        }

        if (!supportsSnowball) {
            VirtualThreadUtils.throwingForEach(
                    item -> write(item.output(), item.path(), outputMetaData), outputsWithPaths);
            return;
        }

        final var metaData =
                OutputMetaData.resolveOutputMetaData(outputMetaData, outputMetaDataProvider);
        final var anyOutput =
                outputsWithPaths.stream().map(OutputWithPath::output).findAny().orElseThrow();

        try {
            minioClient.uploadSnowballObjects(
                    UploadSnowballObjectsArgs.builder()
                            .bucket(bucket)
                            .objects(createSnowballObjects(outputsWithPaths))
                            .headers(createHeaders(anyOutput, metaData))
                            .userMetadata(createUserMetadata(metaData))
                            .build());

        } catch (final ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException e) {
            throw new StorageClientException(
                    MessageFormat.format(
                            "failed to write for [outputs={0}, outputMetaData={1}]",
                            outputsWithPaths, outputMetaData),
                    e);
        }
    }

    @Override
    public final InputStream read(final @NotNull String path, final boolean doUncompressIfNeeded)
            throws IOException {
        try {
            final var objectResponse =
                    minioClient.getObject(
                            GetObjectArgs.builder().bucket(bucket).object(path).build());

            if (doUncompressIfNeeded) {
                final var contentEncoding =
                        objectResponse.headers().get(HttpHeaders.CONTENT_ENCODING);
                return InputStreamUtils.uncompress(objectResponse, contentEncoding, path);
            }

            return objectResponse;
        } catch (final ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException e) {
            throw new StorageClientException(
                    MessageFormat.format(
                            "failed to read for [path={0}, doUncompressIfNeeded={1}]",
                            path, doUncompressIfNeeded),
                    e);
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
    public boolean exists(final @NotNull String path) throws IOException {
        return !list(path, false).isEmpty();
    }

    @Override
    public final String getSignedUrl(
            final @NotNull String path, final @NotNull OffsetDateTime expiry) throws IOException {
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
            throw new StorageClientException(
                    MessageFormat.format(
                            "failed to get signed url for [path={0}, expiry={1}]", path, expiry),
                    e);
        }
    }

    @Override
    public final String getUrl(final @NotNull String path) {
        throw new NotImplementedException(
                "Minio (actually S3) does not support permanent share URLs? (Maximum is 7 days as"
                        + " per S3 spec)");
    }

    public void createBucket(final String bucketName) throws IOException {
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
            throw new StorageClientException(
                    MessageFormat.format(
                            "failed to create bucket for [bucketName={0}]", bucketName),
                    e);
        }
    }
}
