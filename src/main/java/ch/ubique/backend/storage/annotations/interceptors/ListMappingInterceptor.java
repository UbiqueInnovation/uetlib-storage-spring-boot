package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.*;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.ListMapping;
import ch.ubique.backend.storage.annotations.Storage;
import ch.ubique.backend.storage.logger.LogAtNullableLevel;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ListMappingInterceptor {

    private final Logger logger;
    private final Level level;

    public ListMappingInterceptor(final Logger logger, final Level level) {
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
        final var listMapping = findAnnotation(method, ListMapping.class);

        final var returnType = method.getReturnType();
        final var path =
                getPath(
                        method.getParameters(),
                        allArguments,
                        getPathTemplate(storageAnnotation.basePath(), listMapping.path()));

        final var collection = storageClient.list(path, listMapping.recursive());

        if (Collection.class.isAssignableFrom(returnType)) {
            LogAtNullableLevel.atLevel(logger, level)
                    .log(
                            "listing files from path {} with recursive={}",
                            path,
                            listMapping.recursive());
            return collection;
        }

        if (Stream.class.isAssignableFrom(returnType)) {
            LogAtNullableLevel.atLevel(logger, level)
                    .log(
                            "listing files from path {} with recursive={} and returning as a stream",
                            path,
                            listMapping.recursive());
            return collection.stream();
        }

        if (List.class.isAssignableFrom(returnType)) {
            LogAtNullableLevel.atLevel(logger, level)
                    .log(
                            "listing files from path {} with recursive={} and returning as a list",
                            path,
                            listMapping.recursive());
            return collection.stream().toList();
        }

        if (Set.class.isAssignableFrom(returnType)) {
            LogAtNullableLevel.atLevel(logger, level)
                    .log(
                            "listing files from path {} with recursive={} and returning as a set",
                            path,
                            listMapping.recursive());
            return collection.stream().collect(Collectors.toUnmodifiableSet());
        }

        throw new IllegalStateException(
                MessageFormat.format(
                        "Returning/Converting the collection to type {0} not (yet) supported",
                        returnType));
    }
}
