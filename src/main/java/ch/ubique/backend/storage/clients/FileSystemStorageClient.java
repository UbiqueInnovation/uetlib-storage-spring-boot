package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.Output;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.jetbrains.annotations.Nullable;
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
    public void write(Output output, String path, @Nullable OutputMetaData metaData)
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
        try (final var outputStream = new FileOutputStream(file)) {
            output.uncompressed().write(outputStream);
        }
    }

    @Override
    public InputStream read(String path) throws IOException {
        final var file = new File(Path.of(basePath, path).toUri());
        return new FileInputStream(file);
    }

    @Override
    public boolean delete(String path) throws IOException {
        return Files.deleteIfExists(Path.of(basePath, path));
    }

    @Override
    public String getSignedUrl(String path, OffsetDateTime expiry) {
        return getUrl(path);
    }

    @Override
    public String getUrl(String path) {
        return Path.of(basePath, path).toString();
    }
}
