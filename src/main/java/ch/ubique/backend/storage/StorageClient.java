package ch.ubique.backend.storage;

import ch.ubique.backend.storage.output.Output;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.zip.GZIPInputStream;

public interface StorageClient {

    default void write(@NotNull final Output output, @NotNull final String path)
            throws IOException {
        write(output, path, null);
    }

    void write(@NotNull Output output, @NotNull String path, @Nullable OutputMetaData metaData)
            throws IOException;

    default InputStream read(@NotNull final String path) throws IOException {
        return read(path, true);
    }

    InputStream read(@NotNull String path, boolean doUncompress) throws IOException;

    default boolean delete(@NotNull final String path) throws IOException {
        return delete(path, false);
    }

    boolean delete(@NotNull String path, boolean recursive) throws IOException;

    default Collection<String> list() throws IOException {
        return list("");
    }

    default Collection<String> list(final boolean recursive) throws IOException {
        return list("", recursive);
    }

    default Collection<String> list(@NotNull final String path) throws IOException {
        return list(path, false);
    }

    Collection<String> list(@NotNull String path, boolean recursive) throws IOException;

    String getSignedUrl(@NotNull String path, @NotNull OffsetDateTime expiry);

    String getUrl(@NotNull String path);

    default InputStream doUncompress(final String contentEncoding, final InputStream inputStream)
            throws IOException {
        if ("gzip".equals(contentEncoding)) {
            return new GZIPInputStream(inputStream);
        }
        return inputStream;
    }
}
