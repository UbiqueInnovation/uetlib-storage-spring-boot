package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.*;
import static ch.ubique.backend.storage.logger.LogAtNullableLevel.atLevel;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.ExistsMapping;
import ch.ubique.backend.storage.annotations.Storage;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.lang.reflect.Method;
import java.text.MessageFormat;

public final class ExistsMappingInterceptor {

    private final Logger logger;
    private final Level level;

    public ExistsMappingInterceptor(final Logger logger, final Level level) {
        this.logger = logger;
        this.level = level;
    }

    @RuntimeType
    @SuppressWarnings("unused")
    public Object intercept(
            @Origin final Method method,
            @This final Object thiz,
            @AllArguments final Object[] allArguments)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        final var storageClient = getInaccessibleField(thiz, "storageClient", StorageClient.class);
        final var storageAnnotation = findAnnotation(thiz.getClass(), Storage.class);
        final var existsMapping = findAnnotation(method, ExistsMapping.class);

        final var returnType = method.getReturnType();
        if (existsMapping.path().startsWith("/")) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "ExistsMapping::path cannot start with ''/'' for method {0}::{1}. Got:"
                                    + " {2}",
                            method.getDeclaringClass().getSimpleName(),
                            method.getName(),
                            existsMapping.path()));
        }
        final var path =
                getPath(
                        method.getParameters(),
                        allArguments,
                        getPathTemplate(storageAnnotation.basePath(), existsMapping.path()));

        atLevel(logger, level).log("Checking if path {} exists", path);
        return storageClient.exists(path);
    }
}
