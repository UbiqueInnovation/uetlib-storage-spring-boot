package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.*;
import static ch.ubique.backend.storage.logger.LogAtNullableLevel.atLevel;

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
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.text.MessageFormat;

public final class InputMappingInterceptor {

    private final Logger logger;
    private final Level level;

    public InputMappingInterceptor(final Logger logger, final Level level) {
        this.logger = logger;
        this.level = level;
    }

    @RuntimeType
    @SuppressWarnings({"unused", "java:S3011"})
    public Object intercept(
            @Origin final Method method,
            @This final Object thiz,
            @AllArguments final Object[] allArguments)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        final var storageClient = getInaccessibleField(thiz, "storageClient", StorageClient.class);
        final var storageAnnotation = findAnnotation(thiz.getClass(), Storage.class);
        final var inputMapping = findAnnotation(method, InputMapping.class);
        final var parameterAnnotations = method.getParameterAnnotations();

        final var returnType = method.getReturnType();
        if (inputMapping.path().startsWith("/")) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "InputMapping::path cannot start with ''/'' for method {0}::{1}. Got:"
                                + " {2}",
                            method.getDeclaringClass().getSimpleName(),
                            method.getName(),
                            inputMapping.path()));
        }
        final var path =
                getPath(
                        method.getParameters(),
                        allArguments,
                        getPathTemplate(storageAnnotation.basePath(), inputMapping.path()));
        atLevel(logger, level)
                .log(
                        "reading content of type {} from path {} with doUncompressIfNeeded={}",
                        inputMapping.contentType(),
                        path,
                        inputMapping.doUncompressIfNeeded());
        final var inputStream = storageClient.read(path, inputMapping.doUncompressIfNeeded());

        if (InputStream.class.isAssignableFrom(returnType)) {
            atLevel(logger, level).log("and returning the resulting stream as-is");
            return inputStream;
        }

        if (byte[].class.isAssignableFrom(returnType)) {
            atLevel(logger, level).log("by reading the input stream to a byte[]");
            return InputStreamUtils.readBytesAndClose(inputStream);
        }

        if (ByteFile.class.isAssignableFrom(returnType)) {
            atLevel(logger, level).log(" by reading the input stream to a ByteFile");
            final var bytes = InputStreamUtils.readBytesAndClose(inputStream);
            final var fileName = Path.of(path).getFileName().toString();
            final var contentType = StringUtils.defaultIfBlank(inputMapping.contentType(), null);
            return ByteFile.fromBytes(bytes, fileName, contentType);
        }

        final var objectMapper = getObjectMapper(thiz, allArguments);
        atLevel(logger, level)
                .log(
                        "reading content of type {} from path {} and parsing it to type {} using"
                            + " the supplied object mapper",
                        inputMapping.contentType(),
                        path,
                        returnType);
        return objectMapper.readValue(inputStream, returnType);
    }
}
