package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.getPath;
import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.getPathTemplate;

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
        final var storageClientObject = thiz.getClass().getDeclaredField("storageClient").get(thiz);
        if (!(storageClientObject instanceof final StorageClient storageClient)) {
            throw new IllegalStateException(
                    "field storageClient is not instance of StorageClient.class, but rather: "
                            + storageClientObject.getClass());
        }
        final var storageAnnotation = thiz.getClass().getAnnotation(Storage.class);
        final var deleteMapping = method.getAnnotation(DeleteMapping.class);

        final var returnType = method.getReturnType();
        final var path =
                getPath(
                        method.getParameters(),
                        allArguments,
                        getPathTemplate(storageAnnotation.basePath(), deleteMapping.path()));

        return storageClient.delete(path, deleteMapping.recursive());
    }
}
