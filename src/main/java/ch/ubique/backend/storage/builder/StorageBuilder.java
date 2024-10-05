package ch.ubique.backend.storage.builder;

import ch.ubique.backend.storage.annotations.*;
import ch.ubique.backend.storage.annotations.interceptors.OnlyDocumentationInterceptor;
import ch.ubique.backend.storage.factory.StorageMethodInfo;

import com.google.common.collect.MoreCollectors;
import com.pivovarit.function.exception.WrappedException;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodDelegation;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StorageBuilder<T> {

    private static final Logger logger = LoggerFactory.getLogger(StorageBuilder.class);

    private static final Implementation ONLY_DOCUMENTATION_THROW_INTERCEPTOR =
            MethodDelegation.to(OnlyDocumentationInterceptor.class);

    private final ClassLoader beanClassLoader;
    private final Environment environment;

    public StorageBuilder(
            final ConfigurableBeanFactory beanFactory, final Environment environment) {
        this.beanClassLoader = beanFactory.getBeanClassLoader();
        this.environment = environment;
    }

    private static Map<Class<? extends Annotation>, List<StorageMethodInfo>>
            getStorageMethodsByAnnotation(
                    final Class<?> storageClass,
                    final MergedAnnotations.Search mergedAnnotationsSearch) {
        return Arrays.stream(storageClass.getMethods())
                .map(method -> Pair.of(method, mergedAnnotationsSearch.from(method)))
                // only those meta-annotated by @StorageMapping
                .filter(
                        methodMethodAnnotationPair ->
                                methodMethodAnnotationPair
                                        .getRight()
                                        .isPresent(StorageMapping.class))
                // get the annotation of that method that is meta-annotated by @StorageMapping
                .map(
                        methodMethodAnnotationPair ->
                                storageMethodInfo(
                                        mergedAnnotationsSearch,
                                        methodMethodAnnotationPair.getLeft(),
                                        methodMethodAnnotationPair.getRight()))
                .collect(Collectors.groupingBy(StorageMethodInfo::mappingAnnotationType));
    }

    private static StorageMethodInfo storageMethodInfo(
            final MergedAnnotations.Search mergedAnnotationsSearch,
            final Method method,
            final MergedAnnotations methodAnnotation) {
        return Arrays.stream(method.getAnnotations())
                .map(
                        annotation ->
                                new StorageMethodInfo(
                                        method, methodAnnotation, annotation.annotationType()))
                // get the annotationType of the annotation that is
                // meta-annotated by @StorageMapping
                .filter(
                        storageMethodInfo ->
                                mergedAnnotationsSearch
                                        .from(storageMethodInfo.mappingAnnotationType())
                                        .isPresent(StorageMapping.class))
                .collect(MoreCollectors.onlyElement());
    }

    private static <T> DynamicType.Builder<T> buildDocumentationMethods(
            final DynamicType.Builder<T> b,
            final Map<Class<? extends Annotation>, List<StorageMethodInfo>>
                    annotatedMethodsByAnnotation) {
        var builder = b;
        final var outputMethodInfos =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(OutputMapping.class),
                        List.<StorageMethodInfo>of());
        for (final var outputMethodInfo : outputMethodInfos) {
            builder = buildDocumentationMethod(builder, outputMethodInfo);
        }
        return builder;
    }

    private static <T> DynamicType.Builder<T> buildDocumentationMethod(
            final DynamicType.Builder<T> builder, final StorageMethodInfo methodInfo) {
        final var method = methodInfo.storageMethod();
        final var storageMapping =
                methodInfo.storageMethodAnnotations().get(StorageMapping.class).synthesize();
        if (storageMapping.path().startsWith("/")) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "StorageMapping::path cannot start with ''/'' for method {0}::{1}. Got:"
                                    + " {2}",
                            method.getDeclaringClass().getSimpleName(),
                            method.getName(),
                            storageMapping.path()));
        }
        final var mappingPath = storageMapping.path();
        final var getMappingAnnotation =
                AnnotationDescription.Builder.ofType(GetMapping.class)
                        .defineArray("value", "/" + mappingPath)
                        .build();
        final var returnType =
                TypeDescription.Generic.Builder.parameterizedType(
                                ResponseEntity.class, ProblemDetail.class)
                        .build();
        return builder.defineMethod(method.getName(), returnType, Visibility.PUBLIC)
                .throwing(IOException.class)
                .intercept(ONLY_DOCUMENTATION_THROW_INTERCEPTOR)
                .annotateMethod(getMappingAnnotation);
    }

    @SuppressWarnings("java:S5443")
    private static void writeClassToFileSystem(
            final DynamicType.Unloaded<?> generatedClass, final Environment environment) {
        final var springApplicationName =
                Objects.requireNonNull(environment.getProperty("spring.application.name"));
        final var tempFile = Path.of("/tmp/bytebuddy/").resolve(springApplicationName);
        try {
            generatedClass.saveIn(tempFile.toFile());
        } catch (final IOException e) {
            throw new WrappedException(e);
        }
        logger.trace(
                "saved at {}/{}.class", tempFile, generatedClass.getTypeDescription().getName());
    }

    public Class<Object> buildForDocumentationClass(
            final Class<T> storageInterface,
            final MergedAnnotations.Search mergedAnnotationsSearch,
            final Storage storageAnnotation) {
        final var documentationClassName = storageInterface.getSimpleName() + "Documentation";
        if (storageAnnotation.basePath().startsWith("/")) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "Storage::basePath cannot start with ''/'' for storage {0}. Got: {1}",
                            storageInterface.getSimpleName(), storageAnnotation.basePath()));
        }
        final var basePath =
                Stream.of(storageAnnotation.documentationBaseUrl(), storageAnnotation.basePath())
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.joining("/", "/", ""));
        final var builder =
                new ByteBuddy()
                        .subclass(Object.class)
                        .annotateType(
                                AnnotationDescription.Builder.ofType(
                                                StorageDocumentationRestController.class)
                                        .build())
                        .annotateType(
                                AnnotationDescription.Builder.ofType(RestController.class).build())
                        .annotateType(
                                AnnotationDescription.Builder.ofType(RequestMapping.class)
                                        .defineArray("value", basePath)
                                        .build())
                        .name(documentationClassName)
                        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL);

        final var annotatedMethodsByAnnotation =
                getStorageMethodsByAnnotation(storageInterface, mergedAnnotationsSearch);
        final var unloaded =
                buildDocumentationMethods(builder, annotatedMethodsByAnnotation).make();
        return writeAndLoadClass(unloaded);
    }

    @SuppressWarnings("unchecked")
    private <U> Class<U> writeAndLoadClass(final DynamicType.Unloaded<U> unloaded) {
        // log the classes to the filesystem of trace logging is enabled :D
        if (logger.isTraceEnabled()) {
            writeClassToFileSystem(unloaded, environment);
        }

        try {
            return (Class<U>)
                    unloaded.load(beanClassLoader, ClassLoadingStrategy.Default.INJECTION)
                            .getLoaded();
        } catch (final IllegalStateException e) {
            // in case the class is already loaded, just return the loaded class
            final var classToLoad = unloaded.getTypeDescription().getCanonicalName();
            try {
                return (Class<U>) beanClassLoader.loadClass(classToLoad);
            } catch (final ClassNotFoundException ex) {
                // looks like the class was ot already loaded as we thought...
                throw new WrappedException(ex);
            }
        }
    }
}
