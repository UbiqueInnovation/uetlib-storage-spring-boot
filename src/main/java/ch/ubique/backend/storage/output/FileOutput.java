package ch.ubique.backend.storage.output;

import ch.ubique.backend.core.utils.StringUtils;

import jakarta.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class FileOutput implements Output {

    private final File file;
    private final String contentType;

    public FileOutput(final File file) {
        this(file, null);
    }

    public FileOutput(final File file, @Nullable final String contentType) {
        this.file = file;
        this.contentType = StringUtils.requireNonBlankElse(contentType, "application/octet-stream");
    }

    @Override
    public void write(final OutputStream outputStream) throws IOException {
        FileUtils.copyFile(file, outputStream);
    }

    @Override
    public InputStream read() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("contentType", contentType)
                .append("file", file.getAbsolutePath())
                .toString();
    }

    @Override
    public long uncompressedContentLength() {
        return file.length();
    }

    @Override
    public boolean canBeRetried() {
        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileOutput that)) {
            return false;
        }
        return new EqualsBuilder()
                .append(file, that.file)
                .append(contentType, that.contentType)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(file).append(contentType).toHashCode();
    }
}
