package ch.ubique.backend.storage.annotations;

import static org.springframework.beans.factory.config.AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR;
import static org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY;

import static java.lang.reflect.Modifier.FINAL;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.interceptors.DeleteMappingInterceptor;
import ch.ubique.backend.storage.annotations.interceptors.InputMappingInterceptor;
import ch.ubique.backend.storage.annotations.interceptors.ListMappingInterceptor;
import ch.ubique.backend.storage.annotations.interceptors.OutputMappingInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.google.common.collect.MoreCollectors;
import com.pivovarit.function.exception.WrappedException;

import jakarta.validation.constraints.NotNull;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.*;
import net.bytebuddy.implementation.MethodCall.WithoutSpecifiedTarget;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageRegistrar implements BeanDefinitionRegistryPostProcessor {

    private static final String STORAGE_CLIENT_PARAMETER = "storageClient";
    private static final String OBJECT_MAPPER_PARAMETER = "objectMapper";
    private static final Implementation ONLY_DOCUMENTATION_THROW_INTERCEPTOR =
            ExceptionMethod.throwing(
                    IllegalCallerException.class,
                    "this storage method exists only to document blob storage endpoints and must not be called");

    private static final Logger logger = LoggerFactory.getLogger(StorageRegistrar.class);

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

    private static <T> ReceiverTypeDefinition<T> buildConstructor(
            final DynamicType.Builder.FieldDefinition.Optional.Valuable<T> builder,
            final Storage storageAnnotation) {
        final var shouldInjectObjectMapper =
                Boolean.parseBoolean(String.valueOf(storageAnnotation.injectObjectMapper()));

        var constructorBuilder =
                // the public constructor with arguments
                builder.defineConstructor(Visibility.PUBLIC)
                        // parameter 0: storageClient
                        .withParameter(StorageClient.class, STORAGE_CLIENT_PARAMETER);
        if (StringUtils.isNotBlank(storageAnnotation.clientQualifier())) {
            constructorBuilder =
                    constructorBuilder.annotateParameter(
                            AnnotationDescription.Builder.ofType(Qualifier.class)
                                    .define("value", storageAnnotation.clientQualifier())
                                    .build());
        }

        if (shouldInjectObjectMapper) {
            constructorBuilder =
                    constructorBuilder
                            // parameter 1: objectMapper iff shouldInjectObjectMapper == true
                            .withParameter(ObjectMapper.class, OBJECT_MAPPER_PARAMETER);
            if (StringUtils.isNotBlank(storageAnnotation.objectMapperQualifier())) {
                constructorBuilder =
                        constructorBuilder.annotateParameter(
                                AnnotationDescription.Builder.ofType(Qualifier.class)
                                        .define("value", storageAnnotation.objectMapperQualifier())
                                        .build());
            }
        }

        final var setStorageClient =
                FieldAccessor.ofField(STORAGE_CLIENT_PARAMETER).setsArgumentAt(0);
        final var objectMapperAccessor = FieldAccessor.ofField(OBJECT_MAPPER_PARAMETER);
        final Implementation.Composable setObjectMapper;
        if (shouldInjectObjectMapper) {
            setObjectMapper = objectMapperAccessor.setsArgumentAt(1);
        } else {
            setObjectMapper = objectMapperAccessor.setsValue(new ObjectMapper());
        }

        final var callSuperConstructor = getCallSuperConstructor();
        return constructorBuilder.intercept(
                callSuperConstructor.andThen(setStorageClient).andThen(setObjectMapper));
    }

    @NotNull
    private static WithoutSpecifiedTarget getCallSuperConstructor() {
        final WithoutSpecifiedTarget callSuperConstructor;
        try {
            callSuperConstructor = MethodCall.invoke(Object.class.getConstructor());
        } catch (final NoSuchMethodException e) {
            // fatal: no way to handle "Object" not having a constructor ;)
            throw new WrappedException(e);
        }
        return callSuperConstructor;
    }

    private static <T> DynamicType.@NotNull Unloaded<T> generateImplementationClass(
            final Class<T> storageInterface,
            final MergedAnnotations.Search mergedAnnotationsSearch,
            final Storage storageAnnotation) {
        final var implementationClassName = storageInterface.getSimpleName() + "Implementation";
        final var builderForConstructor =
                new ByteBuddy()
                        .subclass(storageInterface, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                        .annotateType(AnnotationDescription.Builder.ofType(Service.class).build())
                        .name(implementationClassName)
                        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL)
                        .defineField(
                                STORAGE_CLIENT_PARAMETER,
                                StorageClient.class,
                                Visibility.PRIVATE,
                                FieldManifestation.FINAL)
                        .defineField(
                                OBJECT_MAPPER_PARAMETER,
                                ObjectMapper.class,
                                Visibility.PRIVATE,
                                FieldManifestation.FINAL);
        final var builderForMethods = buildConstructor(builderForConstructor, storageAnnotation);

        final var annotatedMethodsByAnnotation =
                getStorageMethodsByAnnotation(storageInterface, mergedAnnotationsSearch);
        return buildImplementationMethods(builderForMethods, annotatedMethodsByAnnotation).make();
    }

    private static <T> DynamicType.@NotNull Unloaded<Object> generateDocumentationClass(
            final Class<T> storageInterface,
            final MergedAnnotations.Search mergedAnnotationsSearch,
            final Storage storageAnnotation) {
        final var documentationClassName = storageInterface.getSimpleName() + "Documentation";
        final var basePath =
                Stream.of(storageAnnotation.baseUrl(), storageAnnotation.basePath())
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.joining(File.separator));
        final var builder =
                new ByteBuddy()
                        .subclass(Object.class)
                        .annotateType(
                                AnnotationDescription.Builder.ofType(RestController.class)
                                        .define("value", basePath)
                                        .build())
                        .name(documentationClassName)
                        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL);

        final var annotatedMethodsByAnnotation =
                getStorageMethodsByAnnotation(storageInterface, mergedAnnotationsSearch);
        return buildDocumentationMethods(builder, annotatedMethodsByAnnotation).make();
    }

    private static <T> DynamicType.Builder<T> buildImplementationMethods(
            final DynamicType.Builder<T> b,
            final Map<Class<? extends Annotation>, List<StorageMethodInfo>>
                    annotatedMethodsByAnnotation) {
        var builder = b;
        final var outputMethodInfos =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(OutputMapping.class),
                        List.<StorageMethodInfo>of());
        final var inputMethodInfos =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(InputMapping.class),
                        List.<StorageMethodInfo>of());
        final var deleteMethodInfos =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(DeleteMapping.class),
                        List.<StorageMethodInfo>of());
        final var listMethodInfos =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(ListMapping.class),
                        List.<StorageMethodInfo>of());
        for (final var outputMethodInfo : outputMethodInfos) {
            builder = buildImplementationOutputMethod(builder, outputMethodInfo);
        }
        for (final var inputMethodInfo : inputMethodInfos) {
            builder = buildImplementationInputMethod(builder, inputMethodInfo);
        }
        for (final var deleteMethodInfo : deleteMethodInfos) {
            builder = buildImplementationDeleteMethod(builder, deleteMethodInfo);
        }
        for (final var listMethodInfo : listMethodInfos) {
            builder = buildImplementationListMethod(builder, listMethodInfo);
        }
        return builder;
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
        final var mappingPath =
                methodInfo.storageMethodAnnotations().get(StorageMapping.class).synthesize().path();
        final var methodAnnotations =
                List.of(
                        AnnotationDescription.Builder.ofType(GetMapping.class)
                                .defineArray("value", mappingPath)
                                .build());
        final var parameters = Arrays.stream(method.getParameters()).toList();

        return buildMethod(
                builder,
                parameters,
                method,
                ONLY_DOCUMENTATION_THROW_INTERCEPTOR,
                methodAnnotations);
    }

    private static <T> DynamicType.Builder<T> buildMethod(
            final DynamicType.Builder<T> builder,
            final List<Parameter> parameters,
            final Method method,
            final Implementation interceptor) {
        return buildMethod(builder, parameters, method, interceptor, List.of());
    }

    private static <T> DynamicType.Builder<T> buildMethod(
            final DynamicType.Builder<T> builder,
            final List<Parameter> parameters,
            final Method method,
            final Implementation interceptor,
            final List<AnnotationDescription> methodAnnotations) {
        if (parameters.isEmpty()) {
            return builder.defineMethod(method.getName(), method.getReturnType(), Visibility.PUBLIC)
                    .throwing(IOException.class)
                    .intercept(interceptor);
        }

        final var firstParameter =
                parameters.stream().limit(1).collect(MoreCollectors.onlyElement());
        var parametersBuilder =
                builder.defineMethod(method.getName(), method.getReturnType(), Visibility.PUBLIC)
                        .withParameter(
                                firstParameter.getParameterizedType(),
                                firstParameter.getName(),
                                FINAL)
                        .annotateParameter(firstParameter.getAnnotations());

        for (final var parameter : parameters.stream().skip(1).toList()) {
            parametersBuilder =
                    parametersBuilder
                            .withParameter(
                                    parameter.getParameterizedType(), parameter.getName(), FINAL)
                            .annotateParameter(parameter.getAnnotations());
        }
        return parametersBuilder
                .throwing(IOException.class)
                .intercept(interceptor)
                .annotateMethod(methodAnnotations);
    }

    private static <T> DynamicType.Builder<T> buildImplementationOutputMethod(
            final DynamicType.Builder<T> builder, final StorageMethodInfo outputMethodInfo) {
        final var outputMethod = outputMethodInfo.storageMethod();
        final var parameters = Arrays.stream(outputMethod.getParameters()).toList();

        final Implementation interceptor;
        final var outputAnnotation =
                outputMethodInfo.storageMethodAnnotations().get(OutputMapping.class).synthesize();
        if (outputAnnotation.codeGenerationType().generateImplementationCode()) {
            interceptor = MethodDelegation.to(OutputMappingInterceptor.class);
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }

        return buildMethod(builder, parameters, outputMethod, interceptor);
    }

    private static <T> DynamicType.Builder<T> buildImplementationInputMethod(
            final DynamicType.Builder<T> builder, final StorageMethodInfo inputMethodInfo) {
        final var inputMethod = inputMethodInfo.storageMethod();
        final var parameters = Arrays.stream(inputMethod.getParameters()).toList();

        final Implementation interceptor;
        final var inputAnnotation =
                inputMethodInfo.storageMethodAnnotations().get(InputMapping.class).synthesize();
        if (inputAnnotation.codeGenerationType().generateImplementationCode()) {
            interceptor = MethodDelegation.to(InputMappingInterceptor.class);
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }

        return buildMethod(builder, parameters, inputMethod, interceptor);
    }

    private static <T> DynamicType.Builder<T> buildImplementationListMethod(
            final DynamicType.Builder<T> builder, final StorageMethodInfo listMethodInfo) {
        final var listMethod = listMethodInfo.storageMethod();
        final var parameters = Arrays.stream(listMethod.getParameters()).toList();

        final Implementation interceptor;
        final var listAnnotation =
                listMethodInfo.storageMethodAnnotations().get(ListMapping.class).synthesize();
        if (listAnnotation.codeGenerationType().generateImplementationCode()) {
            interceptor = MethodDelegation.to(ListMappingInterceptor.class);
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }

        return buildMethod(builder, parameters, listMethod, interceptor);
    }

    private static <T> DynamicType.Builder<T> buildImplementationDeleteMethod(
            final DynamicType.Builder<T> builder, final StorageMethodInfo deleteMethodInfo) {
        final var deleteMethod = deleteMethodInfo.storageMethod();
        final var parameters = Arrays.stream(deleteMethod.getParameters()).toList();

        final Implementation interceptor;
        final var deleteAnnotation =
                deleteMethodInfo.storageMethodAnnotations().get(DeleteMapping.class).synthesize();
        if (deleteAnnotation.codeGenerationType().generateImplementationCode()) {
            interceptor = MethodDelegation.to(DeleteMappingInterceptor.class);
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }

        return buildMethod(builder, parameters, deleteMethod, interceptor);
    }

    private static Set<Class<?>> findAllStorageClasses(final String buildPropertiesGroup) {
        final var enableJobsClass =
                EnableAnnotationUtils.findEnableClass(buildPropertiesGroup, EnableStorages.class);
        final var enableJobsAnnotation = enableJobsClass.getAnnotation(EnableStorages.class);
        return EnableAnnotationUtils.findAllClassesBy(
                enableJobsAnnotation.storages(),
                enableJobsAnnotation.basePackages(),
                enableJobsAnnotation.basePackageClasses(),
                enableJobsClass.getPackageName(),
                Storage.class);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(final @NotNull BeanDefinitionRegistry registry)
            throws BeansException {
        registerStorageClients((DefaultListableBeanFactory) registry);
    }

    @Override
    public void postProcessBeanFactory(final @NotNull ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        // empty
    }

    public void registerStorageClients(final DefaultListableBeanFactory beanFactory) {
        final var buildProperties = beanFactory.getBean(BuildProperties.class);
        final var storageClasses = findAllStorageClasses(buildProperties.getGroup());

        for (final var storageClass : storageClasses) {
            Assert.isTrue(
                    storageClass.isInterface(),
                    "@Storage can only be specified on an interface, but was specified on "
                            + storageClass);

            registerStorageClient(beanFactory, storageClass);
        }
    }

    private <T> void registerStorageClient(
            final DefaultListableBeanFactory beanFactory, final Class<T> storageInterface) {

        final var mergedAnnotationsSearch =
                MergedAnnotations.search(TYPE_HIERARCHY)
                        .withEnclosingClasses(
                                clazz ->
                                        !ClassUtils.isInnerClass(clazz)
                                                && !ClassUtils.isStaticClass(clazz));
        final var mergedStorageClassAnnotations = mergedAnnotationsSearch.from(storageInterface);
        final var storageAnnotation = mergedStorageClassAnnotations.get(Storage.class).synthesize();

        if (storageAnnotation.codeGenerationType().generateImplementationCode()) {
            try (final var generatedImplementationClass =
                    generateImplementationClass(
                            storageInterface, mergedAnnotationsSearch, storageAnnotation)) {
                registerBeanForGeneratedClass(
                        beanFactory,
                        storageInterface.getSimpleName(),
                        storageInterface.getCanonicalName(),
                        generatedImplementationClass,
                        StorageBeanType.IMPLEMENTATION);
            }
        }

        if (storageAnnotation.codeGenerationType().generateDocumentationCode()) {
            try (final var generatedDocumentationClass =
                    generateDocumentationClass(
                            storageInterface, mergedAnnotationsSearch, storageAnnotation)) {
                registerBeanForGeneratedClass(
                        beanFactory,
                        storageInterface.getSimpleName(),
                        storageInterface.getCanonicalName(),
                        generatedDocumentationClass,
                        StorageBeanType.DOCUMENTATION);
            }
        }
    }

    private <T> void registerBeanForGeneratedClass(
            final DefaultListableBeanFactory beanFactory,
            final String storageInterfaceSimpleName,
            final String storageInterfaceCanonicalName,
            final DynamicType.Unloaded<T> generatedClass,
            final StorageBeanType storageBeanType) {

        // comment in below, only for debugging purposes to write the generated class file
        final var tempFile = Path.of("/tmp/bytebuddy");
        try {
            generatedClass.saveIn(tempFile.toFile());
        } catch (final IOException e) {
            throw new WrappedException(e);
        }
        logger.info(
                "saved at {}/{}.class", tempFile, generatedClass.getTypeDescription().getName());
        // comment in above, only for debugging purposes to write the generated class file

        final var storageClass =
                generatedClass
                        .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                        .getLoaded();
        final var beanDefinitionBuilder =
                BeanDefinitionBuilder.rootBeanDefinition(storageClass)
                        .setAutowireMode(AUTOWIRE_CONSTRUCTOR);
        final var beanDefinition = beanDefinitionBuilder.getBeanDefinition();

        final var baseBeanName =
                CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, storageInterfaceSimpleName);
        final var beanName =
                switch (storageBeanType) {
                    case IMPLEMENTATION -> baseBeanName;
                    case DOCUMENTATION -> baseBeanName + "Documentation";
                };

        beanFactory.registerBeanDefinition(beanName, beanDefinition);
        logger.info(
                "Registered {} bean {} of class {} for storage class {}",
                storageBeanType,
                beanName,
                storageClass.getCanonicalName(),
                storageInterfaceCanonicalName);
    }

    private enum StorageBeanType {
        IMPLEMENTATION,
        DOCUMENTATION
    }

    private record StorageMethodInfo(
            Method storageMethod,
            MergedAnnotations storageMethodAnnotations,
            Class<? extends Annotation> mappingAnnotationType) {}
}
