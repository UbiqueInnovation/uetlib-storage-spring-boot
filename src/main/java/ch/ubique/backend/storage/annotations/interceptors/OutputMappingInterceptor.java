package ch.ubique.backend.storage.annotations.interceptors;

import static ch.ubique.backend.storage.annotations.interceptors.InterceptorUtil.*;

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

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.IntStream;

public final class OutputMappingInterceptor {
    private OutputMappingInterceptor() {}

    @RuntimeType
    @SuppressWarnings("unused")
    public static void intercept(
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
        final var outputMapping = method.getAnnotation(OutputMapping.class);
        final var parameterAnnotations = method.getParameterAnnotations();

        final var output = getOutput(thiz, outputMapping, parameterAnnotations, allArguments);
        final var path =
                getPath(
                        method.getParameters(),
                        allArguments,
                        getPathTemplate(storageAnnotation.basePath(), outputMapping.path()));
        storageClient.write(output, path, null);
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
        Output output;
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
            output = output.gZip();
        }
        return output;
    }
}
