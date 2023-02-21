package ch.ubique.backend.storage;

import ch.ubique.backend.core.http.cache.CacheHeader;
import ch.ubique.backend.storage.annotations.OutputMapping;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.Contract;

public final class OutputMetaData {

    public static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                    .withZone(ZoneId.of("UTC"));
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

    public Instant getBestBefore() {
        return bestBefore;
    }

    public OutputMetaData setBestBefore(@NotNull final Instant bestBefore) {
        Objects.requireNonNull(bestBefore);
        this.bestBefore = bestBefore;
        return this;
    }

    public Instant getNextRefresh() {
        return nextRefresh;
    }

    public OutputMetaData setNextRefresh(@NotNull final Instant nextRefresh) {
        Objects.requireNonNull(nextRefresh);
        this.nextRefresh = nextRefresh;
        return this;
    }

    public Duration getBackoff() {
        return backoff;
    }

    public OutputMetaData setBackoff(@NotNull final Duration backoff) {
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
}
