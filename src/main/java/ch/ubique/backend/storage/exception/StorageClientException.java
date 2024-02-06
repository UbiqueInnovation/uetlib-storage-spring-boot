package ch.ubique.backend.storage.exception;

public class StorageClientException extends RuntimeException {

    public StorageClientException(final Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public StorageClientException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
