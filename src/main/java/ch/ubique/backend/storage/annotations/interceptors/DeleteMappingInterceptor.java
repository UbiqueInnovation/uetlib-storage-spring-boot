package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.*;
import static ch.ubique.backend.storage.logger.LogAtNullableLevel.atLevel;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.DeleteMapping;
import ch.ubique.backend.storage.annotations.Storage;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.lang.reflect.Method;

public final class DeleteMappingInterceptor {

    private final Logger logger;
    private final Level level;

    public DeleteMappingInterceptor(final Logger logger, final Level level) {
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
        final var deleteMapping = findAnnotation(method, DeleteMapping.class);

        final var returnType = method.getReturnType();
        final var path =
                getPath(
                        method.getParameters(),
                        allArguments,
                        getPathTemplate(storageAnnotation.basePath(), deleteMapping.path()));

        atLevel(logger, level)
                .log(
                        "deleting content from path {} with recursive={}",
                        path,
                        deleteMapping.recursive());
        return storageClient.delete(path, deleteMapping.recursive());
    }
}
