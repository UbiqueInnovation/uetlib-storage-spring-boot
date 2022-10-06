package ch.ubique.backend.storage.clients;

import ch.ubique.backend.storage.Output;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

public class FileSystemStorageClient implements StorageClient {

    private final String basePath;

    public FileSystemStorageClient(final String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void write(Output output, String path, OutputMetaData metaData) throws IOException {
        final var file = new File(Path.of(basePath, path).toUri());
        final var parent = file.getParentFile();
        if (!parent.exists() || !parent.mkdirs()) {
            throw new FileNotFoundException("Could not create directory for path " + parent);
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