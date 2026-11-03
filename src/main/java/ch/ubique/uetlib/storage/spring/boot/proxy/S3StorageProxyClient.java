package ch.ubique.uetlib.storage.spring.boot.proxy;

import static ch.ubique.uetlib.core.utils.StringUtils.requireNonBlank;

import static java.util.Objects.requireNonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.List;

/** Streams objects from S3 or a compatible provider using an application-owned SDK client. */
public class S3StorageProxyClient implements StorageProxyClient {
    private static final Logger logger = LoggerFactory.getLogger(S3StorageProxyClient.class);
    private static final List<String> VALIDATOR_HEADERS =
            List.of(
                    HttpHeaders.ETAG,
                    HttpHeaders.LAST_MODIFIED,
                    HttpHeaders.CACHE_CONTROL,
                    HttpHeaders.EXPIRES,
                    HttpHeaders.VARY);

    private final S3Client s3;
    private final String bucket;

    /** The caller owns the SDK client's configuration and lifecycle. */
    public S3StorageProxyClient(final S3Client s3, final String bucket) {
        this.s3 = requireNonNull(s3);
        this.bucket = requireNonBlank(bucket, "bucket must not be blank");
    }

    @Override
    public ResponseEntity<InputStreamResource> proxyStorageRequest(
            final String eTag, final String key) {
        if (key.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        final var request = GetObjectRequest.builder().bucket(bucket).key(key);
        if (!eTag.isBlank()) {
            request.ifNoneMatch(eTag);
        }
        try {
            final var stream = s3.getObject(request.build());
            try {
                return ResponseEntity.ok()
                        .headers(headers(stream.response()))
                        .body(new InputStreamResource(stream));
            } catch (final RuntimeException failure) {
                try {
                    stream.close();
                } catch (final IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        } catch (final S3Exception failure) {
            if (failure.statusCode() == HttpStatus.NOT_MODIFIED.value()) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .headers(validatorHeaders(failure))
                        .build();
            }
            if (failure.statusCode() == HttpStatus.NOT_FOUND.value()) {
                return ResponseEntity.notFound().build();
            }
            logger.warn("S3 storage proxy request failed (status {})", failure.statusCode());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (final SdkException failure) {
            logger.warn("S3 storage proxy transport failed", failure);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static HttpHeaders headers(final GetObjectResponse response) {
        final var headers = new HttpHeaders();

        // set metadata first so the later ones override any clashes that might occur
        headers.setAll(response.metadata());

        headers.set(HttpHeaders.CACHE_CONTROL, response.cacheControl());
        headers.set(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition());
        headers.set(HttpHeaders.CONTENT_ENCODING, response.contentEncoding());
        headers.set(HttpHeaders.CONTENT_TYPE, response.contentType());
        headers.set(HttpHeaders.ETAG, response.eTag());
        if (response.contentLength() != null) {
            headers.setContentLength(response.contentLength());
        }
        if (response.lastModified() != null) {
            headers.setLastModified(response.lastModified());
        }
        return headers;
    }

    private static HttpHeaders validatorHeaders(final S3Exception failure) {
        final var headers = new HttpHeaders();
        final var details = failure.awsErrorDetails();
        if (details != null && details.sdkHttpResponse() != null) {
            final var response = details.sdkHttpResponse();
            for (final var name : VALIDATOR_HEADERS) {
                response.firstMatchingHeader(name).ifPresent(value -> headers.set(name, value));
            }
        }
        return headers;
    }
}
