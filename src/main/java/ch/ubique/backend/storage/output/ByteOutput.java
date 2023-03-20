package ch.ubique.backend.storage.output;

import ch.ubique.backend.core.file.ByteFile;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ByteOutput implements Output, OutputWithLength {

    private final byte[] value;
    private final String contentType;

    public ByteOutput(final byte[] value) {
        this(value, "application/octet-stream");
    }

    public ByteOutput(final ByteFile byteFile) {
        this(byteFile.getBytes(), byteFile.getContentType());
    }

    public ByteOutput(final byte[] value, final String contentType) {
        this.value = value;
        this.contentType = contentType;
    }

    @Override
    public void write(final OutputStream outputStream) throws IOException {
        outputStream.write(value);
    }

    @Override
    public InputStream read() throws IOException {
        return new ByteArrayInputStream(value);
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("contentType", contentType)
                .append("value.length", value.length)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ByteOutput that)) {
            return false;
        }

        return new EqualsBuilder()
                .append(value, that.value)
                .append(contentType, that.contentType)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(value).append(contentType).toHashCode();
    }

    @Override
    public long contentLength() {
        return value.length;
    }
}
