package ch.ubique.backend.storage.exception;

public final class NonRetryableStorageClientException extends StorageClientException {

    public NonRetryableStorageClientException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
