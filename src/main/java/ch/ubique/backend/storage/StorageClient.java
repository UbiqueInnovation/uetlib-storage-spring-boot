package ch.ubique.backend.storage;

import ch.ubique.backend.storage.output.Output;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;

public interface StorageClient {

    default void write(final Output output, final String path) throws IOException {
        write(output, path, null);
    }

    @NotNull
    default OutputMetaData resolveOutputMetaData(
            final OutputMetaData outputMetaData,
            final OutputMetaDataProvider outputMetaDataProvider,
            final Output output) {
        final OutputMetaData resolvedOutputMetaData;
        if (outputMetaData != null) {
            resolvedOutputMetaData = outputMetaData;
        } else if (outputMetaDataProvider != null) {
            resolvedOutputMetaData = outputMetaDataProvider.createOutputMetaData();
        } else {
            resolvedOutputMetaData = OutputMetaData.defaults();
        }

        resolvedOutputMetaData.contentLengthUncompressed(output.contentLength());

        return resolvedOutputMetaData;
    }

    void write(Output output, String path, @Nullable OutputMetaData metaData) throws IOException;

    InputStream read(String path) throws IOException;

    boolean delete(String path) throws IOException;

    String getSignedUrl(String path, OffsetDateTime expiry);

    String getUrl(String path);
}
