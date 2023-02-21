package ch.ubique.backend.storage;

import ch.ubique.backend.core.http.cache.CacheHeader;
import ch.ubique.backend.core.http.cache.CacheHeaderUtil;
import ch.ubique.backend.storage.annotations.OutputMapping;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Contract;

public final class OutputMetaData {
    @NotNull private final Duration maxAge;
    @NotNull private final Duration sMaxAge;

    private Instant bestBefore;
    private Instant nextRefresh;
    private Duration backoff;

    /**
     * @param maxAge Duration must not be null
     * @param sMaxAge Duration will be set to maxAge if null
     */
    @Contract("null, _ -> fail")
    public OutputMetaData(@NotNull final Duration maxAge, @Nullable final Duration sMaxAge) {
        Objects.requireNonNull(maxAge);
        this.maxAge = maxAge;
        this.sMaxAge = Objects.requireNonNullElse(sMaxAge, maxAge);
    }

    /**
     * This is used if neither a {@link OutputMetaData} is passed to the {@link
     * OutputMapping}/{@link StorageClient#write} method nor a default is provided to the {@link
     * StorageClient}.
     *
     * <p>Sets both {@code max-age} and {@code s-maxage} to one day.
     */
    public static OutputMetaData defaults() {
        return new OutputMetaData(Duration.ofDays(1), Duration.ofDays(1));
    }

    @Contract("null -> fail")
    public OutputMetaData bestBefore(@NotNull final Instant bestBefore) {
        Objects.requireNonNull(bestBefore);
        this.bestBefore = bestBefore;
        return this;
    }

    @Contract("null -> fail")
    public OutputMetaData nextRefresh(@NotNull final Instant nextRefresh) {
        Objects.requireNonNull(nextRefresh);
        this.nextRefresh = nextRefresh;
        return this;
    }

    @Contract("null -> fail")
    public OutputMetaData backoff(@NotNull final Duration backoff) {
        Objects.requireNonNull(backoff);
        this.backoff = backoff;
        return this;
    }

    public String createCacheControl() {
        return String.join(
                ", ",
                CacheHeader.MAX_AGE.value() + "=" + maxAge.getSeconds(),
                CacheHeader.S_MAX_AGE.value() + "=" + sMaxAge.getSeconds());
    }

    public Map<String, String> createMinioMetaDataMap() {
        final var map = new HashMap<String, String>();

        if (nextRefresh != null) {
            map.put(
                    CacheHeader.NEXT_REFRESH.value(),
                    CacheHeaderUtil.formatHttpHeaderTimestamp(nextRefresh));
        }
        if (bestBefore != null) {
            map.put(
                    CacheHeader.BEST_BEFORE.value(),
                    CacheHeaderUtil.formatHttpHeaderTimestamp(bestBefore));
        }
        if (backoff != null) {
            map.put(CacheHeader.BACKOFF.value(), String.valueOf(backoff.getSeconds()));
        }

        return Collections.unmodifiableMap(map);
    }

    public Map<String, String> createAzureMetaDataMap() {
        final var map = new HashMap<String, String>();

        if (nextRefresh != null) {
            map.put(
                    CacheHeader.NEXT_REFRESH.noDashesAzure(),
                    CacheHeaderUtil.formatHttpHeaderTimestamp(nextRefresh));
        }
        if (bestBefore != null) {
            map.put(
                    CacheHeader.BEST_BEFORE.noDashesAzure(),
                    CacheHeaderUtil.formatHttpHeaderTimestamp(bestBefore));
        }
        if (backoff != null) {
            map.put(CacheHeader.BACKOFF.noDashesAzure(), String.valueOf(backoff.getSeconds()));
        }

        return Collections.unmodifiableMap(map);
    }
}
