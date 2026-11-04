package ch.uetlib.storage.spring.boot.proxy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.uetlib.core.http.header.HttpHeader;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class S3StorageProxyClientTest {
    private final S3Client s3 = mock(S3Client.class);
    private final S3StorageProxyClient client = new S3StorageProxyClient(s3, "bucket");

    @Test
    void streamsOneConditionalGetWithItsOwnMetadata() throws Exception {
        final var source = spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final var modified = Instant.parse("2026-10-19T12:00:00Z");
        final var metadata =
                GetObjectResponse.builder()
                        .contentLength(3L)
                        .contentType("application/vnd.ant.fit")
                        .cacheControl("public, max-age=60")
                        .contentDisposition("attachment; filename=course.fit")
                        .contentEncoding("gzip")
                        .eTag("\"version\"")
                        .lastModified(modified)
                        .metadata(Map.of("next-refresh", "60", "content-length", "999"))
                        .build();
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenReturn(
                        new ResponseInputStream<>(metadata, AbortableInputStream.create(source)));
        final var result = client.proxyStorageRequest("W/\"old\", \"other\"", "v1/land/course.fit");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        final var headers = result.getHeaders();
        assertThat(headers.getContentLength()).isEqualTo(3);
        assertThat(headers.getLastModified()).isEqualTo(modified.toEpochMilli());
        assertThat(headers.getFirst(HttpHeaders.LAST_MODIFIED)).contains("GMT");
        assertThat(headers.getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/vnd.ant.fit");
        assertThat(headers.getCacheControl()).isEqualTo("public, max-age=60");
        assertThat(headers.getETag()).isEqualTo("\"version\"");
        assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("course.fit");
        assertThat(headers.getFirst(HttpHeaders.CONTENT_ENCODING)).isEqualTo("gzip");
        assertThat(headers.getFirst(HttpHeader.NEXT_REFRESH.value())).isEqualTo("60");

        // actual content-length overrides content-length in metadata
        assertThat(headers.getFirst(HttpHeaders.CONTENT_LENGTH)).isEqualTo("3");

        try (final var stream = result.getBody().getInputStream()) {
            assertThat(stream.readAllBytes()).containsExactly(1, 2, 3);
        }
        verify(source).close();
        verify(s3)
                .getObject(
                        GetObjectRequest.builder()
                                .bucket("bucket")
                                .key("v1/land/course.fit")
                                .ifNoneMatch("W/\"old\", \"other\"")
                                .build());
        verifyNoMoreInteractions(s3);
    }

    @Test
    void omitsAnAbsentConditionalHeaderAndAllowsMissingMetadata() throws Exception {
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenReturn(
                        new ResponseInputStream<>(
                                GetObjectResponse.builder().build(),
                                AbortableInputStream.create(
                                        new ByteArrayInputStream(new byte[0]))));
        final var response = client.proxyStorageRequest("", "key");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(-1);
        assertThat(response.getHeaders().getLastModified()).isEqualTo(-1);
        response.getBody().getInputStream().close();
        verify(s3).getObject(GetObjectRequest.builder().bucket("bucket").key("key").build());
    }

    @Test
    void returns304WithOnlyCacheValidatorsAndNoBody() {
        final var http =
                SdkHttpResponse.builder()
                        .statusCode(304)
                        .headers(
                                Map.of(
                                        "ETag",
                                        List.of("\"version\""),
                                        "Cache-Control",
                                        List.of("public, max-age=60"),
                                        "Content-Length",
                                        List.of("999"),
                                        "Connection",
                                        List.of("close")))
                        .build();
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenThrow(
                        S3Exception.builder()
                                .statusCode(304)
                                .awsErrorDetails(
                                        AwsErrorDetails.builder().sdkHttpResponse(http).build())
                                .build());
        final var response = client.proxyStorageRequest("\"version\"", "key");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getETag()).isEqualTo("\"version\"");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=60");
        assertThat(response.getHeaders().containsHeader("Content-Length")).isFalse();
        assertThat(response.getHeaders().containsHeader("Connection")).isFalse();
    }

    @Test
    void accepts304WithoutAwsErrorDetails() {
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(304).build());
        assertThat(client.proxyStorageRequest("*", "key").getStatusCode())
                .isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void distinguishesMissingObjectsFromProviderAndTransportFailures() {
        for (final var status : new int[] {404, 403, 500, 503}) {
            when(s3.getObject(any(GetObjectRequest.class)))
                    .thenThrow(S3Exception.builder().statusCode(status).build());
            assertThat(client.proxyStorageRequest("", "key").getStatusCode())
                    .isEqualTo(status == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY);
        }
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("unreachable"));
        assertThat(client.proxyStorageRequest("", "key").getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void validatesConfigurationAndEmptyKeysWithoutCallingTheProvider() {
        assertThatIllegalArgumentException().isThrownBy(() -> new S3StorageProxyClient(s3, " "));
        assertThatNullPointerException().isThrownBy(() -> new S3StorageProxyClient(null, "bucket"));
        assertThat(client.proxyStorageRequest("", " ").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(s3);
    }

    @Test
    void closesTheStreamIfResponseConstructionFails() throws Exception {
        final var source = spy(new ByteArrayInputStream(new byte[0]));
        final var metadata = mock(GetObjectResponse.class);
        when(metadata.metadata()).thenThrow(new IllegalArgumentException("invalid metadata"));
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenReturn(
                        new ResponseInputStream<>(metadata, AbortableInputStream.create(source)));
        assertThatThrownBy(() -> client.proxyStorageRequest("", "key"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(source).close();
    }
}
