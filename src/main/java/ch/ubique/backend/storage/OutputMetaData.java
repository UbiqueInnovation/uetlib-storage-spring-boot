package ch.ubique.backend.storage;

import ch.ubique.backend.core.http.cache.CacheHeaderUtility;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;

/**
 * Object used by the output handler holding meta information about the object to output. Not all
 * output handler support metadata.
 *
 * @author alig
 */
public final class OutputMetaData {

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

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Instant getBestBefore() {
        return bestBefore;
    }

    public void setBestBefore(Instant bestBefore) {
        this.bestBefore = bestBefore;
    }

    public Instant getNextRefresh() {
        return nextRefresh;
    }

    public void setNextRefresh(Instant nextRefresh) {
        this.nextRefresh = nextRefresh;
    }

    public String getCache() {
        return cache;
    }

    public void setCache(String cache) {
        this.cache = cache;
    }

    public Long getBackoff() {
        return backoff;
    }

    public void setBackoff(Long backoff) {
        this.backoff = backoff;
    }

    /**
     * @return the minimumApiVersion
     */
    public Long getMinimumApiVersion() {
        return minimumApiVersion;
    }

    /**
     * @param minimumApiVersion the minimumApiVersion to set
     */
    public void setMinimumApiVersion(Long minimumApiVersion) {
        this.minimumApiVersion = minimumApiVersion;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public Long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Long maxAge) {
        this.maxAge = maxAge;
    }

    public Long getsMaxAge() {
        return sMaxAge;
    }

    public OutputMetaData setsMaxAge(Long sMaxAge) {
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
}
