package ch.ubique.backend.storage.logger;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

public final class LogAtNullableLevel {

    private LogAtNullableLevel() {}

    public static LoggingEventBuilder atLevel(
            @NotNull final Logger logger, @Nullable final Level level) {
        return level == null ? new NullLoggingEventBuilder() : logger.atLevel(level);
    }
}
