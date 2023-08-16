package ch.ubique.backend.storage;

import ch.ubique.backend.core.http.header.HttpHeader;
import ch.ubique.backend.core.http.header.HttpHeaderUtil;
import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.output.Output;

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

public final class OutputMetaData {
    private static final Logger logger = LoggerFactory.getLogger(OutputMetaData.class);

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
                            HttpHeaderUtil.formatHttpHeaderTimestamp(nextRefresh)));
        }
        if (bestBefore != null) {
            entries.add(
                    Map.entry(
                            HttpHeader.BEST_BEFORE,
                            HttpHeaderUtil.formatHttpHeaderTimestamp(bestBefore)));
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
