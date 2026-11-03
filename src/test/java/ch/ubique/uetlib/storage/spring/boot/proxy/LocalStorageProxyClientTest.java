package ch.ubique.uetlib.storage.spring.boot.proxy;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class LocalStorageProxyClientTest {
    @TempDir Path directory;

    @Test
    void streamsBytesWithMetadataAndSupportsCompleteConditionalHeaders() throws Exception {
        final var root = Files.createDirectory(directory.resolve("root"));
        Files.createDirectories(root.resolve("v1/land"));
        final var bytes = "{\"routes\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(root.resolve("v1/land/summary.json"), bytes);
        final var client = new LocalStorageProxyClient(root);
        final var response = client.proxyStorageRequest("", "v1/land/summary.json");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
        assertThat(response.getHeaders().getLastModified()).isPositive();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
        try (final var stream = response.getBody().getInputStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(bytes);
        }
        final var etag = response.getHeaders().getETag();
        for (final var conditional : new String[] {etag, "W/" + etag, "\"other\", " + etag, "*"}) {
            final var unchanged = client.proxyStorageRequest(conditional, "v1/land/summary.json");
            assertThat(unchanged.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
            assertThat(unchanged.getBody()).isNull();
            assertThat(unchanged.getHeaders().getETag()).isEqualTo(etag);
            assertThat(unchanged.getHeaders().getContentLength()).isEqualTo(-1);
        }
        final var changed = client.proxyStorageRequest("\"other\"", "v1/land/summary.json");
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        changed.getBody().getInputStream().close();
    }

    @Test
    void supportsMediaTypeOverridesAndUnknownFileTypes() throws Exception {
        Files.write(directory.resolve("course.CUSTOM"), new byte[] {1, 2, 3});
        final var mediaType = MediaType.parseMediaType("application/x-custom");
        final var client = new LocalStorageProxyClient(directory, Map.of("custom", mediaType));
        var response = client.proxyStorageRequest("", "course.CUSTOM");
        assertThat(response.getHeaders().getContentType()).isEqualTo(mediaType);
        response.getBody().getInputStream().close();
        Files.write(directory.resolve("binary.uetlib_unknown_format"), new byte[] {1});
        response = client.proxyStorageRequest("", "binary.uetlib_unknown_format");
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        response.getBody().getInputStream().close();
    }

    @Test
    void rejectsMissingFilesDirectoriesInvalidKeysAndTraversal() throws Exception {
        final var root = Files.createDirectory(directory.resolve("root"));
        final var outside = Files.writeString(directory.resolve("outside.txt"), "private");
        final var client = new LocalStorageProxyClient(root);
        for (final var key :
                new String[] {
                    "",
                    "missing.txt",
                    ".",
                    "../outside.txt",
                    "nested/../../outside.txt",
                    outside.toString(),
                    "nul\0.txt"
                }) {
            assertThat(client.proxyStorageRequest("", key).getStatusCode())
                    .as(key)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void rejectsSymlinksOutsideTheRootButAllowsLinksInsideIt() throws Exception {
        final var root = Files.createDirectory(directory.resolve("root"));
        Files.writeString(directory.resolve("outside.txt"), "private");
        Files.createSymbolicLink(root.resolve("escape.txt"), directory.resolve("outside.txt"));
        Files.createSymbolicLink(root.resolve("escape-dir"), directory);
        Files.writeString(root.resolve("inside.txt"), "public");
        Files.createSymbolicLink(root.resolve("inside-link.txt"), root.resolve("inside.txt"));
        final var client = new LocalStorageProxyClient(root);
        assertThat(client.proxyStorageRequest("", "escape.txt").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(client.proxyStorageRequest("", "escape-dir/outside.txt").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        final var response = client.proxyStorageRequest("", "inside-link.txt");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        response.getBody().getInputStream().close();
    }

    @Test
    void nonexistentRootReturnsNotFound() {
        assertThat(
                        new LocalStorageProxyClient(directory.resolve("missing"))
                                .proxyStorageRequest("", "file.txt")
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
