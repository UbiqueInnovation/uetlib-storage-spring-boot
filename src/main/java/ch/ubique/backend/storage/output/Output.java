package ch.ubique.backend.storage.output;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

public sealed interface Output permits GZipOutput, ByteOutput, FileOutput, JsonOutput {

    void write(final OutputStream outputStream) throws IOException;

    InputStream read() throws IOException;

    String contentType();

    default Optional<String> contentEncoding() {
        return Optional.empty();
    }

    default Output gZip() {
        return new GZipOutput(this);
    }

    default boolean isGZip() {
        return this instanceof GZipOutput;
    }

    default Output uncompressed() {
        return this;
    }

    long contentLength();
}
