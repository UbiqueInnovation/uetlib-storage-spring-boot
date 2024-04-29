package ch.ubique.backend.storage.clients;

import static java.util.Objects.*;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.Output;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;

public class RetryStorageClient implements StorageClient {

    private final StorageClient storageClient;
    private final RetryTemplate retryTemplate;

    private static final RetryTemplate DEFAULT_RETRY_TEMPLATE =
            RetryTemplate.builder()
                    .retryOn(IOException.class)
                    .maxAttempts(10)
                    .uniformRandomBackoff(Duration.ofMillis(1), Duration.ofMillis(500))
                    .build();

    public RetryStorageClient(final StorageClient storageClient) {
        this(storageClient, null);
    }

    public RetryStorageClient(
            @NotNull final StorageClient storageClient,
            @Nullable final RetryTemplate retryTemplate) {
        this.storageClient = requireNonNull(storageClient, "Storage client must not be null.");
        this.retryTemplate = requireNonNullElse(retryTemplate, DEFAULT_RETRY_TEMPLATE);
    }

    @Override
    public StorageClient retryable(final RetryTemplate retryTemplate) {
        // Unwrap and wrap again with new retry template
        return withoutRetry().retryable(retryTemplate);
    }

    @Override
    public StorageClient withoutRetry() {
        return storageClient.withoutRetry();
    }

    @Override
    public void write(
            final Output output, final String path, @Nullable final OutputMetaData outputMetaData)
            throws IOException {
        retryTemplate.execute(
                (RetryCallback<Void, IOException>)
                        context -> {
                            storageClient.write(output, path, outputMetaData);
                            return null;
                        });
    }

    @Override
    public InputStream read(final String path, final boolean doUncompressIfNeeded)
            throws IOException {
        return retryTemplate.execute(context -> storageClient.read(path, doUncompressIfNeeded));
    }

    @Override
    public boolean delete(final String path, final boolean recursive) throws IOException {
        return retryTemplate.execute(context -> storageClient.delete(path, recursive));
    }

    @Override
    public Collection<String> list(final String path, final boolean recursive) throws IOException {
        return retryTemplate.execute(context -> storageClient.list(path, recursive));
    }

    @Override
    public boolean exists(String path) throws IOException {
        return retryTemplate.execute(context -> storageClient.exists(path));
    }

    @Override
    public String getSignedUrl(final String path, final OffsetDateTime expiry) {
        return retryTemplate.execute(context -> storageClient.getSignedUrl(path, expiry));
    }

    @Override
    public String getUrl(final String path) {
        return retryTemplate.execute(context -> storageClient.getUrl(path));
    }
}
