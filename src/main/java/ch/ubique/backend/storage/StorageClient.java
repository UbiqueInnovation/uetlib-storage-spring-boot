package ch.ubique.backend.storage;

import ch.ubique.backend.storage.clients.RetryStorageClient;
import ch.ubique.backend.storage.exception.StorageClientException;
import ch.ubique.backend.storage.output.Output;
import ch.ubique.backend.storage.output.OutputWithPath;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.springframework.retry.support.RetryTemplate;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collection;

public interface StorageClient {

    void write(
            @NotNull Output output, @NotNull String path, @Nullable OutputMetaData outputMetaData)
            throws StorageClientException;

    void write(
            @NotNull final Collection<OutputWithPath> outputs,
            @Nullable final OutputMetaData outputMetaData)
            throws StorageClientException;

    InputStream read(@NotNull String path, boolean doUncompressIfNeeded)
            throws StorageClientException;

    boolean delete(@NotNull String path, boolean recursive) throws StorageClientException;

    Collection<String> list(@NotNull String path, boolean recursive) throws StorageClientException;

    boolean exists(@NotNull String path) throws StorageClientException;

    String getSignedUrl(@NotNull String path, @NotNull OffsetDateTime expiry)
            throws StorageClientException;

    String getUrl(@NotNull String path);

    default void write(@NotNull final Output output, @NotNull final String path)
            throws StorageClientException {
        write(output, path, null);
    }

    default InputStream read(@NotNull final String path) throws StorageClientException {
        return read(path, true);
    }

    default boolean delete(@NotNull final String path) throws StorageClientException {
        return delete(path, false);
    }

    default Collection<String> list() throws StorageClientException {
        return list("");
    }

    default Collection<String> list(final boolean recursive) throws StorageClientException {
        return list("", recursive);
    }

    default Collection<String> list(@NotNull final String path) throws StorageClientException {
        return list(path, false);
    }

    default StorageClient retryable(@Nullable final RetryTemplate retryTemplate) {
        return new RetryStorageClient(this, retryTemplate);
    }

    default StorageClient retryable() {
        return retryable(null);
    }

    default StorageClient withoutRetry() {
        return this;
    }
}
