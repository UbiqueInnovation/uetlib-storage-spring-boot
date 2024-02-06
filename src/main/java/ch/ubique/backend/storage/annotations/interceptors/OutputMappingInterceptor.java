package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.*;
import static ch.ubique.backend.storage.logger.LogAtNullableLevel.atLevel;

import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.annotations.Storage;
import ch.ubique.backend.storage.annotations.StorageObject;
import ch.ubique.backend.storage.output.ByteOutput;
import ch.ubique.backend.storage.output.FileOutput;
import ch.ubique.backend.storage.output.JsonOutput;
import ch.ubique.backend.storage.output.Output;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.stream.IntStream;

public final class OutputMappingInterceptor {

    private final Logger logger;
    private final Level level;

    public OutputMappingInterceptor(final Logger logger, final Level level) {
        this.logger = logger;
        this.level = level;
    }

    private static OutputMetaData getOutputMetaData(final Object[] allArguments) {
        return Arrays.stream(allArguments)
                .filter(OutputMetaData.class::isInstance)
                .map(OutputMetaData.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static Output getOutput(
            final Object thiz,
            final OutputMapping outputMapping,
            final Annotation[][] parameterAnnotations,
            final Object[] allArguments) {
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
                                                "OutputMapping requires one argument to be annotated with '@StorageObject'"));
        final var storageObject = allArguments[storageObjectParameterIndex];
        final var contentType = outputMapping.contentType();

        final Output output;
        // TODO Java 21 switch case pattern matching
        if (storageObject instanceof final Output o) {
            // no wrapping if already of type Output
            output = o;
        } else if (storageObject instanceof final File file) {
            // wrap in FileOutput
            if (StringUtils.isEmpty(contentType)) {
                output = new FileOutput(file);
            } else {
                output = new FileOutput(file, contentType);
            }
        } else if (storageObject instanceof final byte[] bytes) {
            // wrap in ByteOutput
            if (StringUtils.isEmpty(contentType)) {
                output = new ByteOutput(bytes);
            } else {
                output = new ByteOutput(bytes, contentType);
            }
        } else {
            // default case wrap in JsonOutput
            final var objectMapper = getObjectMapper(thiz, allArguments);
            output = new JsonOutput<>(storageObject, objectMapper);
        }

        if (outputMapping.gZip()) {
            return output.gZip();
        }
        return output;
    }

    @RuntimeType
    @SuppressWarnings("unused")
    public void intercept(
            @Origin final Method method,
            @This final Object thiz,
            @AllArguments final Object[] allArguments)
            throws IOException, NoSuchFieldException, IllegalAccessException {

        final var storageClient = getInaccessibleField(thiz, "storageClient", StorageClient.class);
        final var storageAnnotation = findAnnotation(thiz.getClass(), Storage.class);
        final var outputMapping = findAnnotation(method, OutputMapping.class);
        final var parameterAnnotations = method.getParameterAnnotations();

        final var output = getOutput(thiz, outputMapping, parameterAnnotations, allArguments);
        if (outputMapping.path().startsWith("/")) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "OutputMapping::path cannot start with ''/'' for method {0}::{1}. Got: {2}",
                            method.getDeclaringClass().getSimpleName(),
                            method.getName(),
                            outputMapping.path()));
        }
        final var path =
                getPath(
                        method.getParameters(),
                        allArguments,
                        getPathTemplate(storageAnnotation.basePath(), outputMapping.path()));
        final var outputMetaData = getOutputMetaData(allArguments);

        atLevel(logger, level)
                .log(
                        "writing content of type {} to path {} with size {} and metadata {}",
                        outputMapping.contentType(),
                        path,
                        output.contentLength(),
                        outputMetaData);
        storageClient.write(output, path, outputMetaData);
    }
}
