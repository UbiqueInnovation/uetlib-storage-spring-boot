package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.*;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.DeleteMapping;
import ch.ubique.backend.storage.annotations.Storage;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import java.io.IOException;
import java.lang.reflect.Method;

public class DeleteMappingInterceptor {
    private DeleteMappingInterceptor() {}

    @RuntimeType
    @SuppressWarnings("unused")
    public static Object intercept(
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

        return storageClient.delete(path, deleteMapping.recursive());
    }
}
