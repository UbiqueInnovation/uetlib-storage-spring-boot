package ch.ubique.backend.storage;

import ch.ubique.backend.core.http.cache.CacheHeaderUtility;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public final class OutputMetaData {

    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
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

    public OutputMetaData setContentType(final String contentType) {
        this.contentType = contentType;
        return this;
    }

    public Instant getBestBefore() {
        return bestBefore;
    }

    public OutputMetaData setBestBefore(final Instant bestBefore) {
        this.bestBefore = bestBefore;
        return this;
    }

    public Instant getNextRefresh() {
        return nextRefresh;
    }

    public OutputMetaData setNextRefresh(final Instant nextRefresh) {
        this.nextRefresh = nextRefresh;
        return this;
    }

    public String getCache() {
        return cache;
    }

    public OutputMetaData setCache(final String cache) {
        this.cache = cache;
        return this;
    }

    public Long getBackoff() {
        return backoff;
    }

    public OutputMetaData setBackoff(final Long backoff) {
        this.backoff = backoff;
        return this;
    }

    public Long getMinimumApiVersion() {
        return minimumApiVersion;
    }

    public OutputMetaData setMinimumApiVersion(final Long minimumApiVersion) {
        this.minimumApiVersion = minimumApiVersion;
        return this;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public OutputMetaData setContentEncoding(final String contentEncoding) {
        this.contentEncoding = contentEncoding;
        return this;
    }

    public Long getMaxAge() {
        return maxAge;
    }

    public OutputMetaData setMaxAge(final Long maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public Long getsMaxAge() {
        return sMaxAge;
    }

    public OutputMetaData setsMaxAge(final Long sMaxAge) {
        this.sMaxAge = sMaxAge;
        return this;
    }

    public String getCacheControl() {
        final var parts = new ArrayList<>();
        if (StringUtils.isNotBlank(cache)) {
            parts.add(cache);
        }
        if (maxAge > 0) {
            parts.add(CacheHeaderUtility.MAX_AGE + "=" + maxAge);
        }
        if (sMaxAge >= 0) {
            parts.add(CacheHeaderUtility.S_MAX_AGE + "=" + sMaxAge);
        }

        return parts.isEmpty() ? null : StringUtils.join(", ", parts);
    }

    public Map<String, String> createMetaDataMap() {
        return Map.ofEntries(
                Map.entry(
                        CacheHeaderUtility.NEXT_REFRESH_SHORT,
                        HTTP_DATE_FORMATTER.format(nextRefresh)),
                Map.entry(
                        CacheHeaderUtility.BEST_BEFORE_SHORT,
                        HTTP_DATE_FORMATTER.format(bestBefore)),
                Map.entry(CacheHeaderUtility.CACHE_SHORT, cache),
                Map.entry(CacheHeaderUtility.REFRESH_BACKOFF_SHORT, backoff.toString()),
                Map.entry(
                        CacheHeaderUtility.MINIMUM_API_VERSION_SHORT,
                        minimumApiVersion.toString()));
    }
}
