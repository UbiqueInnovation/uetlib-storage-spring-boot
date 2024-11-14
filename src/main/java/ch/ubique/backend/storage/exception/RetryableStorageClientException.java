package ch.ubique.backend.storage.exception;

public final class RetryableStorageClientException extends StorageClientException {

    public RetryableStorageClientException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
