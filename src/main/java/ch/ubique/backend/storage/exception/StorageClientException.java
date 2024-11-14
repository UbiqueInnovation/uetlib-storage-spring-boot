package ch.ubique.backend.storage.exception;

import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;

import javax.net.ssl.SSLHandshakeException;

public abstract sealed class StorageClientException extends IOException
        permits NonRetryableStorageClientException, RetryableStorageClientException {

    protected StorageClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public static StorageClientException of(final String message, final Throwable cause) {
        if (cause instanceof ConnectException
                || cause instanceof InterruptedIOException
                || cause instanceof SSLHandshakeException
                || cause instanceof EOFException
                // Minio specific
                || cause instanceof InsufficientDataException
                || cause instanceof InvalidResponseException
                || cause instanceof ErrorResponseException
                || cause instanceof InternalException
                || cause instanceof ServerException) {
            return new RetryableStorageClientException(message, cause);
        }
        return new NonRetryableStorageClientException(message, cause);
    }

    public boolean isRetryable() {
        return this instanceof RetryableStorageClientException;
    }
}
