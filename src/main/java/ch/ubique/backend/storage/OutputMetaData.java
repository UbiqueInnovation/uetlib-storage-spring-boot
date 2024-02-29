package ch.ubique.backend.storage;

import static java.util.Map.entry;

import ch.ubique.backend.core.http.header.HeaderUtility;
import ch.ubique.backend.core.http.header.HttpHeader;
import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.output.Output;

import io.minio.messages.CannedAcl;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class OutputMetaData {
    private static final Logger logger = LoggerFactory.getLogger(OutputMetaData.class);

    @NotNull private final Duration maxAge;
    @NotNull private final Duration sMaxAge;

    private Instant bestBefore;
    private Instant nextRefresh;
    private Duration backoff;
    private Long contentLengthUncompressed;
    private CannedAcl accessPolicy;
    private String customAccessPolicy;

    /**
     * @param maxAge Duration must not be null
     * @param sMaxAge Duration will be set to maxAge if null
     */
    public OutputMetaData(@NotNull final Duration maxAge, @Nullable final Duration sMaxAge) {
        Objects.requireNonNull(maxAge);
        this.maxAge = maxAge;
        this.sMaxAge = Objects.requireNonNullElse(sMaxAge, maxAge);
    }

    public OutputMetaData(@NotNull final OutputMetaData outputMetaData) {
        this.maxAge = outputMetaData.maxAge;
        this.sMaxAge = outputMetaData.sMaxAge;
        this.bestBefore = outputMetaData.bestBefore;
        this.nextRefresh = outputMetaData.nextRefresh;
        this.backoff = outputMetaData.backoff;
        this.contentLengthUncompressed = outputMetaData.contentLengthUncompressed;
        this.accessPolicy = outputMetaData.accessPolicy;
        this.customAccessPolicy = outputMetaData.customAccessPolicy;
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

    @NotNull
    @Contract("!null, _, _ -> param1; null, !null, _ -> new")
    public static OutputMetaData resolveOutputMetaData(
            final OutputMetaData outputMetaData,
            final OutputMetaDataProvider outputMetaDataProvider,
            @NotNull final Output output) {
        final OutputMetaData resolvedOutputMetaData;
        if (outputMetaData != null) {
            resolvedOutputMetaData = new OutputMetaData(outputMetaData);
        } else if (outputMetaDataProvider != null) {
            resolvedOutputMetaData = outputMetaDataProvider.createOutputMetaData();
        } else {
            resolvedOutputMetaData = OutputMetaData.defaults();
        }

        if (resolvedOutputMetaData.contentLengthUncompressed != null
                && !resolvedOutputMetaData.contentLengthUncompressed.equals(
                        output.uncompressedContentLength())) {
            logger.warn(
                    "resolvedOutputMetaData.contentLengthUncompressed was already set to '{}' and"
                            + " is now overwritten with '{}'",
                    resolvedOutputMetaData.contentLengthUncompressed,
                    output.uncompressedContentLength());
        }
        resolvedOutputMetaData.contentLengthUncompressed(output.uncompressedContentLength());

        return resolvedOutputMetaData;
    }

    public OutputMetaData bestBefore(@NotNull final Instant bestBefore) {
        Objects.requireNonNull(bestBefore);
        this.bestBefore = bestBefore;
        return this;
    }

    public OutputMetaData nextRefresh(@NotNull final Instant nextRefresh) {
        Objects.requireNonNull(nextRefresh);
        this.nextRefresh = nextRefresh;
        return this;
    }

    public OutputMetaData backoff(@NotNull final Duration backoff) {
        Objects.requireNonNull(backoff);
        this.backoff = backoff;
        return this;
    }

    /**
     * Set an access policy for the object. This is only used for AWS S3. To set a custom access
     * policy, use {@link #customAccessPolicy(String)}.
     *
     * @throws IllegalStateException if {@link #customAccessPolicy(String)} was already called
     * @param accessPolicy The access policy
     * @return this to allow chaining
     */
    public OutputMetaData accessPolicy(@NotNull final CannedAcl accessPolicy)
            throws IllegalStateException {
        if (customAccessPolicy != null) {
            throw new IllegalStateException(
                    "Cannot set access policy and custom access policy at the same time");
        }
        Objects.requireNonNull(accessPolicy);
        this.accessPolicy = accessPolicy;
        return this;
    }

    /**
     * Set a custom access policy for the object. This is only used for AWS S3. To set a predefined
     * access policy, use {@link #accessPolicy(CannedAcl)}. This method allows every string value,
     * thus the user is responsible for the correctness of the value.
     *
     * @throws IllegalStateException if {@link #accessPolicy(CannedAcl)} was already called
     * @param customAccessPolicy The custom access policy
     * @return this to allow chaining
     */
    public OutputMetaData customAccessPolicy(@NotNull final String customAccessPolicy)
            throws IllegalStateException {
        if (accessPolicy != null) {
            throw new IllegalStateException(
                    "Cannot set access policy and custom access policy at the same time");
        }
        Objects.requireNonNull(customAccessPolicy);
        this.customAccessPolicy = customAccessPolicy;
        return this;
    }

    public OutputMetaData contentLengthUncompressed(@NotNull final Long contentLengthUncompressed) {
        Objects.requireNonNull(contentLengthUncompressed);
        this.contentLengthUncompressed = contentLengthUncompressed;
        return this;
    }

    public String createCacheControl() {
        return CacheControl.maxAge(maxAge).sMaxAge(sMaxAge).getHeaderValue();
    }

    public Optional<String> createCannedAcl() {
        return Optional.ofNullable(customAccessPolicy)
                .or(() -> Optional.ofNullable(accessPolicy).map(CannedAcl::toString));
    }

    private List<Entry<HttpHeader, String>> createMetaDataMapEntries() {
        final var entries = new ArrayList<Entry<HttpHeader, String>>();

        if (nextRefresh != null) {
            entries.add(
                    entry(
                            HttpHeader.NEXT_REFRESH,
                            HeaderUtility.formatHttpHeaderDate(nextRefresh)));
        }
        if (bestBefore != null) {
            entries.add(
                    entry(HttpHeader.BEST_BEFORE, HeaderUtility.formatHttpHeaderDate(bestBefore)));
        }
        if (backoff != null) {
            entries.add(entry(HttpHeader.BACKOFF, String.valueOf(backoff.getSeconds())));
        }
        if (contentLengthUncompressed != null) {
            entries.add(
                    entry(
                            HttpHeader.CONTENT_LENGTH_UNCOMPRESSED,
                            String.valueOf(contentLengthUncompressed)));
        }

        return entries;
    }

    public Map<String, String> createMinioMetaDataMap() {
        return createMetaDataMapUsingHeaderMapper(HttpHeader::value);
    }

    public Map<String, String> createAzureMetaDataMap() {
        return createMetaDataMapUsingHeaderMapper(HttpHeader::noDashesAzure);
    }

    private Map<String, String> createMetaDataMapUsingHeaderMapper(
            final Function<HttpHeader, String> keyMapper) {
        return createMetaDataMapEntries().stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                entry -> keyMapper.apply(entry.getKey()), Entry::getValue));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof final OutputMetaData that)) {
            return false;
        }

        return new EqualsBuilder()
                .append(maxAge, that.maxAge)
                .append(sMaxAge, that.sMaxAge)
                .append(bestBefore, that.bestBefore)
                .append(nextRefresh, that.nextRefresh)
                .append(backoff, that.backoff)
                .append(contentLengthUncompressed, that.contentLengthUncompressed)
                .append(accessPolicy, that.accessPolicy)
                .append(customAccessPolicy, that.customAccessPolicy)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(maxAge)
                .append(sMaxAge)
                .append(bestBefore)
                .append(nextRefresh)
                .append(backoff)
                .append(contentLengthUncompressed)
                .append(accessPolicy)
                .append(customAccessPolicy)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("maxAge", maxAge)
                .append("sMaxAge", sMaxAge)
                .append("bestBefore", bestBefore)
                .append("nextRefresh", nextRefresh)
                .append("backoff", backoff)
                .append("contentLengthUncompressed", contentLengthUncompressed)
                .toString();
    }
}
