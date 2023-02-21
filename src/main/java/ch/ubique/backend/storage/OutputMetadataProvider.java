package ch.ubique.backend.storage;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class OutputMetadataProvider {

    private final Duration maxAge;
    private final Duration sMaxAge;
    private Duration bestBeforeOffset;
    private Duration nextRefreshOffset;
    private Duration backoff;

    public OutputMetadataProvider(
            @NotNull final Duration maxAge, @Nullable final Duration sMaxAge) {
        Objects.requireNonNull(maxAge);
        this.maxAge = maxAge;
        this.sMaxAge = Objects.requireNonNullElse(sMaxAge, maxAge);
    }

    public OutputMetadataProvider bestBeforeOffset(@NotNull final Duration bestBeforeOffset) {
        Objects.requireNonNull(bestBeforeOffset);
        this.bestBeforeOffset = bestBeforeOffset;
        return this;
    }

    public OutputMetadataProvider nextRefreshOffset(@NotNull final Duration nextRefreshOffset) {
        Objects.requireNonNull(nextRefreshOffset);
        this.nextRefreshOffset = nextRefreshOffset;
        return this;
    }

    public OutputMetadataProvider backoff(@NotNull final Duration backoff) {
        Objects.requireNonNull(backoff);
        this.backoff = backoff;
        return this;
    }

    public OutputMetaData createOutputMetaData() {
        final var outputMetaData = new OutputMetaData(maxAge, sMaxAge);

        final var now = Instant.now();

        if (bestBeforeOffset != null) {
            outputMetaData.setBestBefore(now.plus(bestBeforeOffset));
        }
        if (nextRefreshOffset != null) {
            outputMetaData.setNextRefresh(now.plus(nextRefreshOffset));
        }

        outputMetaData.setBackoff(backoff);

        return outputMetaData;
    }
}
