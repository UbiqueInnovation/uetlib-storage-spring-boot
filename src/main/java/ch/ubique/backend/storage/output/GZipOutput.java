package ch.ubique.backend.storage.output;


import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.*;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

public record GZipOutput(Output uncompressed) implements Output {

    public GZipOutput(final Output uncompressed) {
        if (uncompressed instanceof final GZipOutput gZipOutput) {
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
    public long contentLength() {
        return uncompressed.contentLength();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uncompressed", uncompressed)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof final GZipOutput that)) {
            return false;
        }

        return new EqualsBuilder().append(uncompressed, that.uncompressed).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uncompressed).toHashCode();
    }
}
