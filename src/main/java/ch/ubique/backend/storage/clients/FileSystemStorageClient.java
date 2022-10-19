package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.Output;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

public class FileSystemStorageClient implements StorageClient {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemStorageClient.class);

    private final String basePath;

    public FileSystemStorageClient(final String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void write(Output output, String path, OutputMetaData metaData) throws IOException {
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
            output.write(outputStream);
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
        return Path.of(basePath, path).toString();
    }
}
