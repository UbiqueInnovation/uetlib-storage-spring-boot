package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.*;

import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.core.utils.InputStreamUtils;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.InputMapping;
import ch.ubique.backend.storage.annotations.Storage;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;

public class InputMappingInterceptor {
    private InputMappingInterceptor() {}

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
        final var inputMapping = method.getAnnotation(InputMapping.class);
        final var parameterAnnotations = method.getParameterAnnotations();

        final var returnType = method.getReturnType();
        final var path =
                getPath(
                        method.getParameters(),
                        allArguments,
                        getPathTemplate(storageAnnotation.basePath(), inputMapping.path()));

        final var inputStream = storageClient.read(path, inputMapping.doUncompressIfNeeded());
        if (InputStream.class.isAssignableFrom(returnType)) {
            return inputStream;
        }

        if (byte[].class.isAssignableFrom(returnType)) {
            return InputStreamUtils.readBytesAndClose(inputStream);
        }

        if (ByteFile.class.isAssignableFrom(returnType)) {
            final var bytes = InputStreamUtils.readBytesAndClose(inputStream);
            final var fileName = Path.of(path).getFileName().toString();
            final var contentType = StringUtils.defaultIfBlank(inputMapping.contentType(), null);
            return ByteFile.fromBytes(bytes, fileName, contentType);
        }

        final var objectMapper = getObjectMapper(thiz, allArguments);
        return objectMapper.readValue(inputStream, returnType);
    }
}
