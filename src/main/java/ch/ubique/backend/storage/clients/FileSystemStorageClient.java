package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.Output;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class FileSystemStorageClient implements StorageClient {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemStorageClient.class);

    private final String basePath;

    public FileSystemStorageClient(final String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void write(
            final Output output, final String path, @Nullable final OutputMetaData metaData)
            throws IOException {
        final var file = new File(Path.of(basePath, path).toUri());
        final var parent = file.getParentFile();
        if (!parent.exists()) {
            synchronized (this) {
                if (!parent.exists()) {
                    logger.info("Creating parent directory {}", parent);
                    final var createdDir = parent.mkdirs();
                    if (!createdDir) {
                        throw new FileNotFoundException(
                                "Could not create directory for path " + parent);
                    }
                }
            }
        }
        logger.trace("writing output to {}", path);
        try (final var outputStream = new FileOutputStream(file)) {
            output.uncompressed().write(outputStream);
        }
    }

    @Override
    public InputStream read(final String path) throws IOException {
        final var file = new File(Path.of(basePath, path).toUri());
        logger.debug("reading input from {}", path);
        return new FileInputStream(file);
    }

    @Override
    public boolean delete(final String path) throws IOException {
        logger.debug("deleting from {}", path);
        return Files.deleteIfExists(Path.of(basePath, path));
    }

    @Override
    public String getSignedUrl(final String path, final OffsetDateTime expiry) {
        return getUrl(path);
    }

    @Override
    public String getUrl(final String path) {
        return Path.of(basePath, path).toString();
    }
}
