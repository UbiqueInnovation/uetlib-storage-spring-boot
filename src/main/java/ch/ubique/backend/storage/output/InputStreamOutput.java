package ch.ubique.backend.storage.output;

import ch.ubique.backend.core.utils.StringUtils;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public final class InputStreamOutput implements Output {

    private final String contentType;
    private final InputStream inputStream;

    private final long contentLength;

    public InputStreamOutput(final InputStream inputStream) {
        this(inputStream, null);
    }

    public InputStreamOutput(final InputStream inputStream, @Nullable final String contentType) {
        this(inputStream, contentType, null);
    }

    public InputStreamOutput(
            final InputStream inputStream,
            @Nullable final String contentType,
            @Nullable final Long contentLength) {
        this.inputStream = inputStream;
        this.contentType = StringUtils.requireNonBlankElse(contentType, "application/octet-stream");
        this.contentLength = Objects.requireNonNullElse(contentLength, -1L);
    }

    @Override
    public void write(final OutputStream outputStream) throws IOException {
        inputStream.transferTo(outputStream);
    }

    @Override
    public InputStream read() throws IOException {
        return inputStream;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("contentType", contentType)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof final InputStreamOutput that)) {
            return false;
        }

        return new EqualsBuilder()
                .append(inputStream, that.inputStream)
                .append(contentType, that.contentType)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(inputStream).append(contentType).toHashCode();
    }

    @Override
    public long contentLength() {
        return contentLength;
    }
}
