package ch.ubique.backend.storage.invocation;

import static ch.ubique.backend.storage.invocation.InvocationHandlerUtil.*;
import static ch.ubique.backend.storage.logger.LogAtNullableLevel.atLevel;

import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.core.utils.ArrayUtils;
import ch.ubique.backend.core.utils.InputStreamUtils;
import ch.ubique.backend.storage.annotations.InputMapping;
import ch.ubique.backend.storage.builder.StorageMappingInvocationProperties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;

public class InputMappingInvocationHandler implements StorageMappingInvocationHandler {

    private final Logger logger;

    public InputMappingInvocationHandler(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public Object invoke(
            final StorageMappingInvocationProperties storageMappingInvocationProperties,
            final Method method,
            final Object[] args)
            throws Throwable {
        final var storageClient = storageMappingInvocationProperties.storageClient();
        final var inputMapping = findAnnotation(method, InputMapping.class);
        final var level = ArrayUtils.singletonOrNull(inputMapping.logLevel());

        final var returnType = method.getReturnType();

        final var basePath = storageMappingInvocationProperties.basePath();
        final var path =
                getPath(
                        method.getParameters(),
                        args,
                        getPathTemplate(basePath, inputMapping.path()));

        // Here, the INPUT really starts

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

        final var objectMapper =
                getObjectMapper(storageMappingInvocationProperties.objectMapper(), args);
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
