package ch.ubique.backend.storage;

import jakarta.annotation.Nullable;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;

import org.jetbrains.annotations.Contract;
import org.springframework.http.CacheControl;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class OutputMetaDataProvider {

    private final CacheControl cacheControl;
    private Duration expiresOffset;
    private Duration bestBeforeOffset;
    private Duration nextRefreshOffset;
    private Duration backoff;

    /**
     * @param cacheControl Cache control must not be null
     */
    @Contract("null -> fail")
    public OutputMetaDataProvider(@NotNull final CacheControl cacheControl) {
        this.cacheControl = Objects.requireNonNull(cacheControl);
        if (this.cacheControl.equals(CacheControl.empty())) {
            throw new ValidationException("An empty cache control is disallowed.");
        }
    }

    /**
     * @param maxAge Duration must not be null
     * @param sMaxAge Duration will be set to maxAge if null
     */
    @Contract("null, _ -> fail")
    public OutputMetaDataProvider(
            @NotNull final Duration maxAge, @Nullable final Duration sMaxAge) {
        this(
                CacheControl.maxAge(Objects.requireNonNull(maxAge))
                        .sMaxAge(Objects.requireNonNullElse(sMaxAge, maxAge)));
    }

    @Contract("null -> fail")
    public OutputMetaDataProvider expiresOffset(@NotNull final Duration expiresOffset) {
        this.expiresOffset = Objects.requireNonNull(expiresOffset);
        return this;
    }

    @Contract("null -> fail")
    public OutputMetaDataProvider bestBeforeOffset(@NotNull final Duration bestBeforeOffset) {
        Objects.requireNonNull(bestBeforeOffset);
        this.bestBeforeOffset = bestBeforeOffset;
        return this;
    }

    @Contract("null -> fail")
    public OutputMetaDataProvider nextRefreshOffset(@NotNull final Duration nextRefreshOffset) {
        Objects.requireNonNull(nextRefreshOffset);
        this.nextRefreshOffset = nextRefreshOffset;
        return this;
    }

    @Contract("null -> fail")
    public OutputMetaDataProvider backoff(@NotNull final Duration backoff) {
        Objects.requireNonNull(backoff);
        this.backoff = backoff;
        return this;
    }

    public OutputMetaData createOutputMetaData() {
        final var outputMetaData = new OutputMetaData(cacheControl);

        final var now = Instant.now();
        if (expiresOffset != null) {
            outputMetaData.expires(now.plus(expiresOffset));
        }
        if (bestBeforeOffset != null) {
            outputMetaData.bestBefore(now.plus(bestBeforeOffset));
        }
        if (nextRefreshOffset != null) {
            outputMetaData.nextRefresh(now.plus(nextRefreshOffset));
        }
        if (backoff != null) {
            outputMetaData.backoff(backoff);
        }

        return outputMetaData;
    }
}
