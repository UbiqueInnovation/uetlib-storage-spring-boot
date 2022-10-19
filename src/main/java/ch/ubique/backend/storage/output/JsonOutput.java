package ch.ubique.backend.storage.output;

import ch.ubique.backend.storage.Output;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class JsonOutput<T> implements Output {

    private final T value;
    private final ObjectMapper objectMapper;

    public JsonOutput(final T value) {
        this(value, new ObjectMapper());
    }

    public JsonOutput(final T value, final ObjectMapper objectMapper) {
        this.value = value;
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(final OutputStream outputStream) throws IOException {
        objectMapper.writeValue(outputStream, value);
    }

    @Override
    public InputStream read() throws IOException {
        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(value));
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("value", value)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JsonOutput<?> that)) {
            return false;
        }
        return new EqualsBuilder().append(value, that.value).append(objectMapper, that.objectMapper).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(value).append(objectMapper).toHashCode();
    }
}
