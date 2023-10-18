package ch.ubique.backend.storage.logger;

import org.slf4j.Marker;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.function.Supplier;

/** LoggingEventBuilder that is a no-op. */
public class NullLoggingEventBuilder implements LoggingEventBuilder {
    @Override
    public LoggingEventBuilder setCause(final Throwable cause) {
        return this;
    }

    @Override
    public LoggingEventBuilder addMarker(final Marker marker) {
        return this;
    }

    @Override
    public LoggingEventBuilder addArgument(final Object p) {
        return this;
    }

    @Override
    public LoggingEventBuilder addArgument(final Supplier<?> objectSupplier) {
        return this;
    }

    @Override
    public LoggingEventBuilder addKeyValue(final String key, final Object value) {
        return this;
    }

    @Override
    public LoggingEventBuilder addKeyValue(final String key, final Supplier<Object> valueSupplier) {
        return this;
    }

    @Override
    public LoggingEventBuilder setMessage(final String message) {
        return this;
    }

    @Override
    public LoggingEventBuilder setMessage(final Supplier<String> messageSupplier) {
        return this;
    }

    @Override
    public void log() {
        // noop
    }

    @Override
    public void log(final String message) {
        // noop
    }

    @Override
    public void log(final String message, final Object arg) {
        // noop
    }

    @Override
    public void log(final String message, final Object arg0, final Object arg1) {
        // noop
    }

    @Override
    public void log(final String message, final Object... args) {
        // noop
    }

    @Override
    public void log(final Supplier<String> messageSupplier) {
        // noop
    }
}
