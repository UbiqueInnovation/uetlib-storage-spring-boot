package ch.ubique.backend.storage.output;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public final class FileOutput implements Output {

    private final File file;
    private final String contentType;

    public FileOutput(File file) {
        this(file, "application/octet-stream");
    }

    public FileOutput(File file, String contentType) {
        this.file = file;
        this.contentType = contentType;
    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
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
    public boolean equals(Object o) {
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
