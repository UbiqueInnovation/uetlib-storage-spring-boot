package ch.ubique.backend.storage.clients;

import static java.util.Objects.*;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.Output;
import ch.ubique.backend.storage.output.OutputWithPath;

import com.pivovarit.function.ThrowingRunnable;

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

    private static final RetryTemplate DEFAULT_RETRY_TEMPLATE =
            RetryTemplate.builder()
                    .retryOn(Exception.class)
                    .maxAttempts(10)
                    .uniformRandomBackoff(Duration.ofMillis(1), Duration.ofMillis(500))
                    .build();

    private final StorageClient storageClient;
    private final RetryTemplate retryTemplate;

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
            final @NotNull Output output,
            final @NotNull String path,
            @Nullable final OutputMetaData outputMetaData)
            throws IOException {
        if (output.canBeRetried()) {
            retryRunnable(() -> storageClient.write(output, path, outputMetaData));
        } else {
            storageClient.write(output, path, outputMetaData);
        }
    }

    @Override
    public void write(
            final Collection<OutputWithPath> outputs, @Nullable final OutputMetaData outputMetaData)
            throws IOException {
        if (outputs.stream().map(OutputWithPath::output).allMatch(Output::canBeRetried)) {
            retryRunnable(() -> storageClient.write(outputs, outputMetaData));
        } else {
            storageClient.write(outputs, outputMetaData);
        }
    }

    @Override
    public InputStream read(final @NotNull String path, final boolean doUncompressIfNeeded)
            throws IOException {
        return retryTemplate.execute(context -> storageClient.read(path, doUncompressIfNeeded));
    }

    @Override
    public boolean delete(final @NotNull String path, final boolean recursive) throws IOException {
        return retryTemplate.execute(context -> storageClient.delete(path, recursive));
    }

    @Override
    public Collection<String> list(final @NotNull String path, final boolean recursive)
            throws IOException {
        return retryTemplate.execute(context -> storageClient.list(path, recursive));
    }

    @Override
    public boolean exists(@NotNull final String path) throws IOException {
        return retryTemplate.execute(context -> storageClient.exists(path));
    }

    @Override
    public String getSignedUrl(final @NotNull String path, final @NotNull OffsetDateTime expiry)
            throws IOException {
        return retryTemplate.execute(context -> storageClient.getSignedUrl(path, expiry));
    }

    @Override
    public String getUrl(final @NotNull String path) {
        return retryTemplate.execute(context -> storageClient.getUrl(path));
    }

    private void retryRunnable(final ThrowingRunnable<IOException> runnable) throws IOException {
        retryTemplate.execute(
                (RetryCallback<Void, IOException>)
                        context -> {
                            runnable.run();
                            return null;
                        });
    }
}
