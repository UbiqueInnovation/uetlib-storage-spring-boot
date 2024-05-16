package ch.ubique.backend.storage.output;

import jakarta.validation.constraints.NotNull;

public record OutputWithPath(@NotNull Output output, @NotNull String path) {}
