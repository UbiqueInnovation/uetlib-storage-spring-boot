package ch.ubique.backend.storage;

import ch.ubique.backend.core.http.cache.CacheHeader;
import ch.ubique.backend.core.http.cache.CacheHeaderUtil;
import ch.ubique.backend.storage.annotations.OutputMapping;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.Contract;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

public final class OutputMetaData {
    @NotNull private final Duration maxAge;
    @NotNull private final Duration sMaxAge;

    private Instant bestBefore;
    private Instant nextRefresh;
    private Duration backoff;
    private Long contentLengthUncompressed;

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

    @Contract("null -> fail")
    public OutputMetaData contentLengthUncompressed(@NotNull final Long contentLengthUncompressed) {
        Objects.requireNonNull(contentLengthUncompressed);
        this.contentLengthUncompressed = contentLengthUncompressed;
        return this;
    }

    public String createCacheControl() {
        return String.join(
                ", ",
                CacheHeader.MAX_AGE.value() + "=" + maxAge.getSeconds(),
                CacheHeader.S_MAX_AGE.value() + "=" + sMaxAge.getSeconds());
    }

    private List<Entry<CacheHeader, String>> createMetaDataMapEntries() {
        final var entries = new ArrayList<Entry<CacheHeader, String>>();

        if (nextRefresh != null) {
            entries.add(
                    Map.entry(
                            CacheHeader.NEXT_REFRESH,
                            CacheHeaderUtil.formatHttpHeaderTimestamp(nextRefresh)));
        }
        if (bestBefore != null) {
            entries.add(
                    Map.entry(
                            CacheHeader.BEST_BEFORE,
                            CacheHeaderUtil.formatHttpHeaderTimestamp(bestBefore)));
        }
        if (backoff != null) {
            entries.add(Map.entry(CacheHeader.BACKOFF, String.valueOf(backoff.getSeconds())));
        }
        if (contentLengthUncompressed != null) {
            entries.add(
                    Map.entry(
                            CacheHeader.CONTENT_LENGTH_UNCOMPRESSED,
                            String.valueOf(contentLengthUncompressed)));
        }

        return entries;
    }

    public Map<String, String> createMinioMetaDataMap() {
        return createMetaDataMapEntries().stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                entry -> entry.getKey().value(), Entry::getValue));
    }

    public Map<String, String> createAzureMetaDataMap() {
        return createMetaDataMapEntries().stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                entry -> entry.getKey().noDashesAzure(), Entry::getValue));
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("maxAge", maxAge)
                .append("sMaxAge", sMaxAge)
                .append("bestBefore", bestBefore)
                .append("nextRefresh", nextRefresh)
                .append("backoff", backoff)
                .append("contentLengthUncompressed", contentLengthUncompressed)
                .toString();
    }
}
