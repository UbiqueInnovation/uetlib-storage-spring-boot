package ch.ubique.backend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import org.jetbrains.annotations.Nullable;

public interface StorageClient {

    default void write(Output output, String path) throws IOException {
        write(output, path, null);
    }

    void write(Output output, String path, @Nullable OutputMetaData metaData) throws IOException;

    InputStream read(String path) throws IOException;

    boolean delete(String path) throws IOException;

    String getSignedUrl(String path, OffsetDateTime expiry);

    String getUrl(String path);
}
