package ch.ubique.backend.storage;

import ch.ubique.backend.core.http.header.HeaderUtility;
import ch.ubique.backend.core.http.header.HttpHeader;
import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.output.Output;

import io.minio.messages.CannedAcl;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @NotNull
    @Contract("!null, _, _ -> param1; null, !null, _ -> new")
    public static OutputMetaData resolveOutputMetaData(
            final OutputMetaData outputMetaData,
            final OutputMetaDataProvider outputMetaDataProvider,
            @NotNull final Output output) {
        final OutputMetaData resolvedOutputMetaData;
        if (outputMetaData != null) {
            resolvedOutputMetaData = outputMetaData;
        } else if (outputMetaDataProvider != null) {
            resolvedOutputMetaData = outputMetaDataProvider.createOutputMetaData();
        } else {
            resolvedOutputMetaData = OutputMetaData.defaults();
        }

        if (resolvedOutputMetaData.contentLengthUncompressed != null
                && !resolvedOutputMetaData.contentLengthUncompressed.equals(
                        output.contentLength())) {
            logger.warn(
                    "resolvedOutputMetaData.contentLengthUncompressed was already set to '{}' and"
                            + " is now overwritten with '{}'",
                    resolvedOutputMetaData.contentLengthUncompressed,
                    output.contentLength());
        }
        resolvedOutputMetaData.contentLengthUncompressed(output.contentLength());

        return resolvedOutputMetaData;
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

    /**
     * Set an access policy for the object. This is only used for AWS S3. To set a custom access
     * policy, use {@link #customAccessPolicy(String)}.
     *
     * @throws IllegalStateException if {@link #customAccessPolicy(String)} was already called
     * @param accessPolicy The access policy
     * @return this to allow chaining
     */
    @Contract("null -> fail")
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
    @Contract("null -> fail")
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

    @Contract("null -> fail")
    public OutputMetaData contentLengthUncompressed(@NotNull final Long contentLengthUncompressed) {
        Objects.requireNonNull(contentLengthUncompressed);
        this.contentLengthUncompressed = contentLengthUncompressed;
        return this;
    }

    public String createCacheControl() {
        return CacheControl.maxAge(maxAge).sMaxAge(sMaxAge).getHeaderValue();
    }

    private List<Entry<HttpHeader, String>> createMetaDataMapEntries() {
        final var entries = new ArrayList<Entry<HttpHeader, String>>();

        if (nextRefresh != null) {
            entries.add(
                    Map.entry(
                            HttpHeader.NEXT_REFRESH,
                            HeaderUtility.formatHttpHeaderDate(nextRefresh)));
        }
        if (bestBefore != null) {
            entries.add(
                    Map.entry(
                            HttpHeader.BEST_BEFORE,
                            HeaderUtility.formatHttpHeaderDate(bestBefore)));
        }
        if (backoff != null) {
            entries.add(Map.entry(HttpHeader.BACKOFF, String.valueOf(backoff.getSeconds())));
        }
        if (contentLengthUncompressed != null) {
            entries.add(
                    Map.entry(
                            HttpHeader.CONTENT_LENGTH_UNCOMPRESSED,
                            String.valueOf(contentLengthUncompressed)));
        }

        return entries;
    }

    private List<Entry<HttpHeader, String>> createMinioMetaDataMapEntries() {
        final var entries = new ArrayList<Entry<HttpHeader, String>>();

        if (customAccessPolicy != null) {
            entries.add(Map.entry(HttpHeader.AMZ_ACL, customAccessPolicy));
        } else if (accessPolicy != null) {
            entries.add(Map.entry(HttpHeader.AMZ_ACL, accessPolicy.toString()));
        }

        return entries;
    }

    public Map<String, String> createMinioMetaDataMap() {
        return Stream.concat(
                        createMetaDataMapEntries().stream(),
                        createMinioMetaDataMapEntries().stream())
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
