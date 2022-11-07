package ch.ubique.backend.storage.output;

import ch.ubique.backend.storage.Output;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public final class GZipOutput implements Output {

    private final Output uncompressed;

    public GZipOutput(final Output uncompressed) {
        if (uncompressed instanceof GZipOutput gZipOutput) {
            this.uncompressed = gZipOutput.uncompressed;
        } else {
            this.uncompressed = uncompressed;
        }
    }

    @Override
    public Output gZip() {
        return this;
    }

    @Override
    public Output uncompressed() {
        return this.uncompressed;
    }

    @Override
    public void write(final OutputStream outputStream) throws IOException {
        final var gZipOutputStream = new GZIPOutputStream(outputStream);
        uncompressed.write(gZipOutputStream);
        gZipOutputStream.finish();
    }

    @Override
    public InputStream read() throws IOException {
        try (final var outputStream = new ByteArrayOutputStream()) {
            write(outputStream);
            final var bytes = outputStream.toByteArray();
            return new ByteArrayInputStream(bytes);
        }
    }

    @Override
    public String contentType() {
        return uncompressed.contentType();
    }

    @Override
    public Optional<String> contentEncoding() {
        return Optional.of("gzip");
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uncompressed", uncompressed)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof GZipOutput that)) return false;

        return new EqualsBuilder().append(uncompressed, that.uncompressed).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uncompressed).toHashCode();
    }
}
