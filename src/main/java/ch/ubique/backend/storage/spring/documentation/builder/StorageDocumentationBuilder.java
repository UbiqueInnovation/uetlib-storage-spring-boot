package ch.ubique.backend.storage.spring.documentation.builder;

import static ch.ubique.backend.storage.builder.StorageBuilderUtil.getStorageMethodsByAnnotation;
import static ch.ubique.backend.storage.spring.documentation.builder.StorageObjectTypeResolver.getStorageObjectParameterType;

import static java.util.Objects.requireNonNull;

import ch.ubique.backend.core.utils.StreamUtils;
import ch.ubique.backend.storage.StorageMappingType;
import ch.ubique.backend.storage.annotations.CodeGenerationType;
import ch.ubique.backend.storage.builder.StorageMethodInfo;
import ch.ubique.backend.storage.spring.annotations.Storage;
import ch.ubique.backend.storage.spring.documentation.annotations.StorageDocumentationRestController;
import ch.ubique.backend.storage.spring.documentation.interceptors.OnlyDocumentationInterceptor;

import com.pivovarit.function.exception.WrappedException;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription.Generic.Builder;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodDelegation;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StorageDocumentationBuilder<T> {

    private static final Logger logger = LoggerFactory.getLogger(StorageDocumentationBuilder.class);

    private static final Implementation ONLY_DOCUMENTATION_THROW_INTERCEPTOR =
            MethodDelegation.to(OnlyDocumentationInterceptor.class);

    private final ClassLoader beanClassLoader;
    private final Environment environment;

    public StorageDocumentationBuilder(
            final ConfigurableBeanFactory beanFactory, final Environment environment) {
        this.beanClassLoader = requireNonNull(beanFactory.getBeanClassLoader());
        this.environment = environment;
    }

    private static <T> DynamicType.Builder<T> buildDocumentationMethods(
            final DynamicType.Builder<T> b,
            final Map<StorageMappingType, List<StorageMethodInfo>> annotatedMethodsByAnnotation) {
        var builder = b;
        final var outputMethodInfos =
                StreamUtils.emptyIfNull(annotatedMethodsByAnnotation.get(StorageMappingType.OUTPUT))
                        .filter(
                                storageMethodInfo ->
                                        storageMethodInfo
                                                .outputMappingAnnotation()
                                                .codeGenerationType()
                                                .generateDocumentationCode())
                        .toList();

        final var outputStorageMethodsByMappingPaths =
                outputMethodInfos.stream()
                        .collect(Collectors.groupingBy(StorageMethodInfo::mappingPath));
        outputStorageMethodsByMappingPaths.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .forEach(
                        entry -> {
                            final var mappingPath = entry.getKey();
                            final var methods =
                                    entry.getValue().stream()
                                            .map(StorageMethodInfo::storageMethod)
                                            .toList();
                            final var methodsString =
                                    methods.stream()
                                            .map(
                                                    m ->
                                                            m.getDeclaringClass().getSimpleName()
                                                                    + "#"
                                                                    + m.getName()
                                                                    + "()")
                                            .collect(Collectors.joining(", ", "{", "}"));
                            final var storageClass =
                                    methods.getFirst().getDeclaringClass().getSimpleName();

                            throw new IllegalArgumentException(
                                    MessageFormat.format(
                                            "Storage {0} is not valid: Methods {1} map to the same documentation path {2}. Deduplicate the mapping paths or set only one of them to {3}.{4}",
                                            storageClass,
                                            methodsString,
                                            mappingPath,
                                            CodeGenerationType.class.getSimpleName(),
                                            CodeGenerationType.DOCUMENTATION.name()));
                        });

        for (final var outputMethodInfo : outputMethodInfos) {
            builder = buildDocumentationMethod(builder, outputMethodInfo);
        }
        return builder;
    }

    private static <T> DynamicType.Builder<T> buildDocumentationMethod(
            final DynamicType.Builder<T> builder, final StorageMethodInfo methodInfo) {
        final var method = methodInfo.storageMethod();
        final var mappingPath = methodInfo.mappingPath();
        final var getMappingAnnotation =
                AnnotationDescription.Builder.ofType(GetMapping.class)
                        .defineArray("value", "/" + mappingPath)
                        .build();
        final var returnType = documentationMethodReturnType(methodInfo);
        return builder.defineMethod(method.getName(), returnType, Visibility.PUBLIC)
                .throwing(IOException.class)
                .intercept(ONLY_DOCUMENTATION_THROW_INTERCEPTOR)
                .annotateMethod(getMappingAnnotation);
    }

    private static TypeDefinition documentationMethodReturnType(
            final StorageMethodInfo methodInfo) {
        final var documentedClass = methodInfo.outputMappingAnnotation().documentedClass();
        if (!documentedClass.equals(Object.class)) {
            // use documentedClass if it is explicitly set (default value is Object.class)
            return Builder.parameterizedType(ResponseEntity.class, documentedClass).build();
        }

        final var type = getStorageObjectParameterType(methodInfo.storageMethod());
        return Builder.parameterizedType(ResponseEntity.class, type).build();
    }

    @SuppressWarnings("java:S5443")
    private static void writeClassToFileSystem(
            final DynamicType.Unloaded<?> generatedClass, final Environment environment) {
        final var springApplicationName =
                requireNonNull(environment.getProperty("spring.application.name"));
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
            final Class<T> storageInterface, final Storage storageAnnotation) {
        final var documentationClassName = storageInterface.getCanonicalName() + "Documentation";

        final var loadedClassIfExists = loadClassIfExists(documentationClassName);
        if (loadedClassIfExists != null) {
            // 1. Try to find the class if it was already created in a previous run/context refresh
            return loadedClassIfExists;
        }
        // 2. Class doesn't exist yet, proceed with Byte Buddy generation

        if (storageAnnotation.documentationBaseUrl().startsWith("/")) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "Storage::documentationBaseUrl cannot start with ''/'' for storage {0}. Got: {1}",
                            storageInterface.getSimpleName(),
                            storageAnnotation.documentationBaseUrl()));
        }
        if (storageAnnotation.basePath().startsWith("/")) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "Storage::basePath cannot start with ''/'' for storage {0}. Got: {1}",
                            storageInterface.getSimpleName(), storageAnnotation.basePath()));
        }
        if (storageAnnotation.documentationBaseUrl().isBlank()
                && storageAnnotation.basePath().isBlank()) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "Storage::documentationBaseUrl and Storage::basePath cannot be blank for storage {0}. Got: {1} and {2}",
                            storageInterface.getSimpleName(),
                            storageAnnotation.documentationBaseUrl(),
                            storageAnnotation.basePath()));
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
                                        .define("storage", storageInterface)
                                        .build())
                        .annotateType(
                                AnnotationDescription.Builder.ofType(RestController.class).build())
                        .annotateType(
                                AnnotationDescription.Builder.ofType(RequestMapping.class)
                                        .defineArray("value", basePath)
                                        .build())
                        .name(documentationClassName)
                        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL);

        final var annotatedMethodsByAnnotation = getStorageMethodsByAnnotation(storageInterface);
        final var unloaded =
                buildDocumentationMethods(builder, annotatedMethodsByAnnotation).make();
        return writeAndLoadClass(storageInterface, unloaded);
    }

    @Nullable
    private Class<Object> loadClassIfExists(final String documentationClassName) {
        try {
            //noinspection unchecked
            return (Class<Object>) Class.forName(documentationClassName, false, beanClassLoader);
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <U> Class<U> writeAndLoadClass(
            final Class<T> storageInterface, final DynamicType.Unloaded<U> unloaded) {
        // log the classes to the filesystem if trace logging is enabled
        if (logger.isTraceEnabled()) {
            writeClassToFileSystem(unloaded, environment);
        }

        try {
            final var packageLookup =
                    MethodHandles.privateLookupIn(storageInterface, MethodHandles.lookup());
            return (Class<U>)
                    unloaded.load(
                                    beanClassLoader,
                                    ClassLoadingStrategy.UsingLookup.of(packageLookup))
                            .getLoaded();
        } catch (final IllegalAccessException e) {
            // not allowed to load that class
            throw new WrappedException(e);
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
