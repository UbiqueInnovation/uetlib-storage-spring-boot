package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.*;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.ListMapping;
import ch.ubique.backend.storage.annotations.Storage;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import java.io.IOException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListMappingInterceptor {
    private ListMappingInterceptor() {}

    @RuntimeType
    @SuppressWarnings("unused")
    public static Object intercept(
            @Origin final Method method,
            @This final Object thiz,
            @AllArguments final Object[] allArguments)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        final var storageClient = getInaccessibleField(thiz, "storageClient", StorageClient.class);
        final var storageAnnotation = findAnnotation(thiz.getClass(), Storage.class);
        final var listMapping = findAnnotation(method,ListMapping.class);

        final var returnType = method.getReturnType();
        final var path =
                getPath(
                        method.getParameters(),
                        allArguments,
                        getPathTemplate(storageAnnotation.basePath(), listMapping.path()));

        final var collection = storageClient.list(path, listMapping.recursive());

        if (Collection.class.isAssignableFrom(returnType)) {
            return collection;
        }

        if (Stream.class.isAssignableFrom(returnType)) {
            return collection.stream();
        }

        if (List.class.isAssignableFrom(returnType)) {
            return collection.stream().toList();
        }

        if (Set.class.isAssignableFrom(returnType)) {
            return collection.stream().collect(Collectors.toUnmodifiableSet());
        }

        throw new IllegalStateException(
                MessageFormat.format(
                        "Returning/Converting the collection to type {0} not (yet) supported",
                        returnType));
    }
}
