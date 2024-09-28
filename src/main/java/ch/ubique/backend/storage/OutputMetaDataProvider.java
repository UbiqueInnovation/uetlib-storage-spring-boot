package ch.ubique.backend.storage;

import io.minio.messages.CannedAcl;

import jakarta.annotation.Nullable;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.CacheControl;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class OutputMetaDataProvider {

    private final @NotNull CacheControl cacheControl;

    private Duration expiresOffset;
    private Duration bestBeforeOffset;
    private Duration nextRefreshOffset;
    private Duration backoff;

    private CannedAcl accessPolicy;
    private String customAccessPolicy;

    /**
     * @param cacheControl Cache control must not be null
     */
    public OutputMetaDataProvider(final @NotNull CacheControl cacheControl) {
        this.cacheControl = Objects.requireNonNull(cacheControl);
        if (this.cacheControl.equals(CacheControl.empty())) {
            throw new ValidationException("An empty cache control is disallowed.");
        }
    }

    /**
     * @param maxAge Duration must not be null
     * @param sMaxAge Duration will be set to maxAge if null
     */
    public OutputMetaDataProvider(
            final @NotNull Duration maxAge, @Nullable final Duration sMaxAge) {
        this(
                CacheControl.maxAge(Objects.requireNonNull(maxAge))
                        .sMaxAge(Objects.requireNonNullElse(sMaxAge, maxAge)));
    }

    /**
     * The predefined offset for the {@code Expires} header. The offset is added to the current
     * timestamp at time of the time of calling {@link #createOutputMetaData()} on this provider.
     *
     * @param expiresOffset the offset for the {@code Expires} header
     * @return this to allow chaining
     */
    public OutputMetaDataProvider expiresOffset(final @NotNull Duration expiresOffset) {
        this.expiresOffset = Objects.requireNonNull(expiresOffset);
        return this;
    }

    /**
     * The predefined offset for the {@code Best-Before} header. The offset is added to the current
     * timestamp at time of the time of calling {@link #createOutputMetaData()} on this provider.
     *
     * @param bestBeforeOffset the offset for the {@code Best-Before} header
     * @return this to allow chaining
     */
    public OutputMetaDataProvider bestBeforeOffset(final @NotNull Duration bestBeforeOffset) {
        Objects.requireNonNull(bestBeforeOffset);
        this.bestBeforeOffset = bestBeforeOffset;
        return this;
    }

    /**
     * The predefined offset for the {@code Next-Refresh} header. The offset is added to the current
     * timestamp at time of the time of calling {@link #createOutputMetaData()} on this provider.
     *
     * @param nextRefreshOffset the offset for the {@code Next-Refresh} header
     * @return this to allow chaining
     */
    public OutputMetaDataProvider nextRefreshOffset(final @NotNull Duration nextRefreshOffset) {
        Objects.requireNonNull(nextRefreshOffset);
        this.nextRefreshOffset = nextRefreshOffset;
        return this;
    }

    public OutputMetaDataProvider backoff(final @NotNull Duration backoff) {
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
    public OutputMetaDataProvider accessPolicy(final @NotNull CannedAcl accessPolicy)
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
    public OutputMetaDataProvider customAccessPolicy(final @NotNull String customAccessPolicy)
            throws IllegalStateException {
        if (accessPolicy != null) {
            throw new IllegalStateException(
                    "Cannot set access policy and custom access policy at the same time");
        }
        Objects.requireNonNull(customAccessPolicy);
        this.customAccessPolicy = customAccessPolicy;
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
