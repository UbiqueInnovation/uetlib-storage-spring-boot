package ch.ubique.backend.storage;

import ch.ubique.backend.storage.output.GZipOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

public interface Output {

    void write(final OutputStream outputStream) throws IOException;

    InputStream read() throws IOException;

    String contentType();

    default Optional<String> contentEncoding() {
        return Optional.empty();
    }

    default Output gZip() {
        return new GZipOutput(this);
    }
}
