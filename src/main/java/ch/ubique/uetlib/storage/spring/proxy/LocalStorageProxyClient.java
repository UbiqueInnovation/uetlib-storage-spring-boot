package ch.ubique.uetlib.storage.spring.proxy;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Serves regular files beneath an explicitly configured local root. */
public class LocalStorageProxyClient implements StorageProxyClient {
    private static final Logger logger = LoggerFactory.getLogger(LocalStorageProxyClient.class);
    private final Path baseDir;
    private final Map<String, MediaType> mediaTypes;

    public LocalStorageProxyClient(final Path baseDir) {
        this(baseDir, Map.of());
    }

    /**
     * @param mediaTypes optional filename-extension overrides, without the leading dot
     */
    public LocalStorageProxyClient(final Path baseDir, final Map<String, MediaType> mediaTypes) {
        this.baseDir = requireNonNull(baseDir).toAbsolutePath().normalize();
        this.mediaTypes = Map.copyOf(mediaTypes);
    }

    @Override
    public ResponseEntity<InputStreamResource> proxyStorageRequest(
            final String eTag, final String key) {
        try {
            if (key.isEmpty() || Path.of(key).isAbsolute()) {
                return ResponseEntity.notFound().build();
            }
            final var file = baseDir.resolve(key).normalize();
            if (!file.startsWith(baseDir) || !Files.isRegularFile(file)) {
                return ResponseEntity.notFound().build();
            }
            // Normalization alone does not prevent a symlink from leaving the configured root.
            final var realFile = file.toRealPath();
            if (!realFile.startsWith(baseDir.toRealPath())) {
                return ResponseEntity.notFound().build();
            }
            final var lastModified = Files.getLastModifiedTime(realFile);
            final var size = Files.size(realFile);
            final var fileETag =
                    "\"%s-%s\""
                            .formatted(
                                    Long.toHexString(lastModified.toMillis()),
                                    Long.toHexString(size));
            final var headers = new HttpHeaders();
            headers.setETag(fileETag);
            headers.setLastModified(lastModified.toInstant());
            headers.setCacheControl(CacheControl.noCache());
            if (matches(eTag, fileETag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
            }
            headers.setContentType(contentType(realFile));
            headers.setContentLength(size);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(Files.newInputStream(realFile)));
        } catch (final InvalidPathException e) {
            return ResponseEntity.notFound().build();
        } catch (final IOException e) {
            logger.debug("Could not load local storage key {}", key, e);
            return ResponseEntity.notFound().build();
        }
    }

    private MediaType contentType(final Path file) throws IOException {
        final var name = file.getFileName().toString();
        final var extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        final var override = mediaTypes.get(extension);
        if (override != null) {
            return override;
        }
        final var detected = Files.probeContentType(file);
        return detected == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(detected);
    }

    private static boolean matches(final String ifNoneMatch, final String eTag) {
        final var headers = new HttpHeaders();
        headers.set(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        try {
            return headers.getIfNoneMatch().stream()
                    .anyMatch(
                            candidate ->
                                    "*".equals(candidate)
                                            || candidate.equals(eTag)
                                            || candidate.equals("W/" + eTag));
        } catch (final IllegalArgumentException e) {
            // Ignore malformed validators rather than failing an otherwise valid file request.
            return false;
        }
    }
}
