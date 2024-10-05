package ch.ubique.backend.storage.invocation;

import static ch.ubique.backend.storage.invocation.InvocationHandlerUtil.*;
import static ch.ubique.backend.storage.logger.LogAtNullableLevel.atLevel;

import ch.ubique.backend.core.utils.ArrayUtils;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.annotations.StorageObject;
import ch.ubique.backend.storage.builder.StorageMappingInvocationProperties;
import ch.ubique.backend.storage.output.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.IntStream;

public class OutputMappingInvocationHandler implements StorageMappingInvocationHandler {

    private final Logger logger;

    public OutputMappingInvocationHandler(final Logger logger) {
        this.logger = logger;
    }

    private static OutputMetaData getOutputMetaData(final Object[] allArguments) {
        return Arrays.stream(allArguments)
                .filter(OutputMetaData.class::isInstance)
                .map(OutputMetaData.class::cast)
                .findFirst()
                .orElse(null);
    }

    private Output getOutput(
            final ObjectMapper objectMapper,
            final OutputMapping outputMapping,
            final Annotation[][] parameterAnnotations,
            final Object[] args) {
        final var storageObjectParameterIndex =
                IntStream.range(0, parameterAnnotations.length)
                        .filter(
                                i ->
                                        Arrays.stream(parameterAnnotations[i])
                                                .anyMatch(StorageObject.class::isInstance))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "OutputMapping requires one argument to be"
                                                        + " annotated with '@StorageObject'"));
        final var storageObject = args[storageObjectParameterIndex];
        final var contentType = outputMapping.contentType();

        final var output =
                switch (storageObject) {
                    case final Output o -> o; // no wrapping if already of type Output
                    case final File file -> new FileOutput(file, contentType);
                    case final InputStream is -> new InputStreamOutput(is, contentType);
                    case final byte[] bytes -> new ByteOutput(bytes, contentType);
                    case null -> new ByteOutput(new byte[0], contentType);
                    default -> new JsonOutput<>(storageObject, objectMapper);
                };

        if (outputMapping.gZip()) {
            return output.gZip();
        }
        return output;
    }

    @Override
    public Object invoke(
            final StorageMappingInvocationProperties storageMappingInvocationProperties,
            final Method method,
            final Object[] args)
            throws Throwable {
        final var storageClient = storageMappingInvocationProperties.storageClient();
        final var objectMapper = storageMappingInvocationProperties.objectMapper();

        final var outputMapping = findAnnotation(method, OutputMapping.class);
        final var parameterAnnotations = method.getParameterAnnotations();
        final var level = ArrayUtils.singletonOrNull(outputMapping.logLevel());

        final var output = getOutput(objectMapper, outputMapping, parameterAnnotations, args);

        final var basePath = storageMappingInvocationProperties.basePath();
        final var path =
                getPath(
                        method.getParameters(),
                        args,
                        getPathTemplate(basePath, outputMapping.path()));
        final var outputMetaData = getOutputMetaData(args);

        atLevel(logger, level)
                .log(
                        "writing content of type {} to path {} with size {} and metadata {}",
                        outputMapping.contentType(),
                        path,
                        output.contentLength(),
                        outputMetaData);
        storageClient.write(output, path, outputMetaData);

        return new Object();
    }
}
