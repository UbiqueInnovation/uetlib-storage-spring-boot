package ch.ubique.backend.storage.exception;

import java.io.IOException;

public class StorageClientException extends IOException {

    public StorageClientException(final Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public StorageClientException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
