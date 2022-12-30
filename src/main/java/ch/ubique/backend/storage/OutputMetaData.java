package ch.ubique.backend.storage;

import ch.ubique.backend.core.http.cache.CacheHeaderUtility;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class OutputMetaData {

    private static DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                    .withZone(ZoneId.of("UTC"));

    private String contentType;
    private String contentEncoding;
    private Instant bestBefore;
    private Instant nextRefresh;
    private String cache;
    private Long backoff;
    private Long maxAge = -1L;
    private Long sMaxAge = Duration.ofDays(1).getSeconds();
    private Long minimumApiVersion;

    public String getContentType() {
        return contentType;
    }

    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    public Instant getBestBefore() {
        return bestBefore;
    }

    public void setBestBefore(final Instant bestBefore) {
        this.bestBefore = bestBefore;
    }

    public Instant getNextRefresh() {
        return nextRefresh;
    }

    public void setNextRefresh(final Instant nextRefresh) {
        this.nextRefresh = nextRefresh;
    }

    public String getCache() {
        return cache;
    }

    public void setCache(final String cache) {
        this.cache = cache;
    }

    public Long getBackoff() {
        return backoff;
    }

    public void setBackoff(final Long backoff) {
        this.backoff = backoff;
    }

    public Long getMinimumApiVersion() {
        return minimumApiVersion;
    }

    public void setMinimumApiVersion(final Long minimumApiVersion) {
        this.minimumApiVersion = minimumApiVersion;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public void setContentEncoding(final String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public Long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(final Long maxAge) {
        this.maxAge = maxAge;
    }

    public Long getsMaxAge() {
        return sMaxAge;
    }

    public OutputMetaData setsMaxAge(final Long sMaxAge) {
        this.sMaxAge = sMaxAge;
        return this;
    }

    public Optional<String> getCacheControl() {
        final var parts = new HashSet<String>();
        if (cache != null && !cache.isBlank()) {
            parts.add(cache);
        }
        if (maxAge > 0) {
            parts.add(CacheHeaderUtility.MAX_AGE + "=" + maxAge);
        }
        if (sMaxAge >= 0) {
            parts.add(CacheHeaderUtility.S_MAX_AGE + "=" + sMaxAge);
        }

        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join(", ", parts));
    }

    @NotNull
    public Map<String, String> createStorageMetaData() {
        final var azureMetaData = new HashMap<String, String>();
        if (nextRefresh != null) {
            azureMetaData.put(
                    CacheHeaderUtility.NEXT_REFRESH_SHORT, HTTP_DATE_FORMATTER.format(nextRefresh));
        }
        if (bestBefore != null) {
            azureMetaData.put(
                    CacheHeaderUtility.BEST_BEFORE_SHORT, HTTP_DATE_FORMATTER.format(bestBefore));
        }
        if (cache != null) {
            azureMetaData.put(CacheHeaderUtility.CACHE_SHORT, cache);
        }
        if (backoff != null) {
            azureMetaData.put(CacheHeaderUtility.REFRESH_BACKOFF_SHORT, backoff.toString());
        }
        azureMetaData.put(
                CacheHeaderUtility.MINIMUM_API_VERSION_SHORT,
                Optional.ofNullable(minimumApiVersion)
                        .orElse(CacheHeaderUtility.DEFAULT_API_VERSION)
                        .toString());

        return azureMetaData;
    }
}
