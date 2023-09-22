package ch.ubique.backend.storage.annotations;

import static org.springframework.beans.factory.config.AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR;
import static org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY;

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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private static final String STORAGE_CLIENT_PARAMETER = "storageClient";
    private static final String OBJECT_MAPPER_PARAMETER = "objectMapper";
    private static final Implementation ONLY_DOCUMENTATION_THROW_INTERCEPTOR =
            ExceptionMethod.throwing(
                    IllegalCallerException.class,
                    "this storage method exists only to document blob storage endpoints and must not be called");

    private static final Logger logger = LoggerFactory.getLogger(StorageRegistrar.class);

    private Environment environment;

    private static Map<Class<? extends Annotation>, List<StorageMethodInfo>>
            getStorageMethodsByAnnotation(
                    final Class<?> storageClass,
                    final MergedAnnotations.Search mergedAnnotationsSearch) {
        return Arrays.stream(storageClass.getDeclaredMethods())
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

        final WithoutSpecifiedTarget callSuperConstructor;
        try {
            callSuperConstructor = MethodCall.invoke(Object.class.getConstructor());
        } catch (final NoSuchMethodException e) {
            // fatal: no way to handle "Object" not having a constructor ;)
            throw new WrappedException(e);
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

        return constructorBuilder.intercept(
                callSuperConstructor.andThen(setStorageClient).andThen(setObjectMapper));
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

    private static <T> DynamicType.@NotNull Unloaded<T> generateDocumentationClass(
            final Class<T> storageInterface,
            final MergedAnnotations.Search mergedAnnotationsSearch,
            final Storage storageAnnotation) {
        final var documentationClassName = storageInterface.getSimpleName() + "Documentation";
        final var builder =
                new ByteBuddy()
                        .subclass(storageInterface, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                        .annotateType(
                                AnnotationDescription.Builder.ofType(RestController.class)
                                        .define("value", storageAnnotation.basePath())
                                        .build())
                        .name(documentationClassName)
                        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL);

        final var annotatedMethodsByAnnotation =
                getStorageMethodsByAnnotation(storageInterface, mergedAnnotationsSearch);
        return buildDocumentationMethods(builder, annotatedMethodsByAnnotation).make();
    }

    private static <T> ReceiverTypeDefinition<T> buildImplementationMethods(
            final ReceiverTypeDefinition<T> b,
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
            builder = buildDocumentationMethod(builder, outputMethodInfo);
        }
        for (final var inputMethodInfo : inputMethodInfos) {
            builder = buildDocumentationMethod(builder, inputMethodInfo);
        }
        for (final var deleteMethodInfo : deleteMethodInfos) {
            builder = buildDocumentationMethod(builder, deleteMethodInfo);
        }
        for (final var listMethodInfo : listMethodInfos) {
            builder = buildDocumentationMethod(builder, listMethodInfo);
        }
        return builder;
    }

    private static <T> DynamicType.Builder<T> buildDocumentationMethod(
            final DynamicType.Builder<T> builder, final StorageMethodInfo methodInfo) {
        final var method = methodInfo.storageMethod();
        final var parameters = Arrays.stream(method.getParameters()).toList();
        final var parameterTypes =
                parameters.stream().map(Parameter::getParameterizedType).toList();
        return builder.defineMethod(method.getName(), method.getReturnType(), Visibility.PUBLIC)
                .withParameters(parameterTypes)
                .throwing(IOException.class)
                .intercept(ONLY_DOCUMENTATION_THROW_INTERCEPTOR);
    }

    private static <T> ReceiverTypeDefinition<T> buildImplementationOutputMethod(
            final ReceiverTypeDefinition<T> builder, final StorageMethodInfo outputMethodInfo) {
        final var outputMethod = outputMethodInfo.storageMethod();
        final var parameters = Arrays.stream(outputMethod.getParameters()).toList();
        final var parameterTypes =
                parameters.stream().map(Parameter::getParameterizedType).toList();
        final Implementation interceptor;
        final var outputAnnotation =
                outputMethodInfo.storageMethodAnnotations().get(OutputMapping.class).synthesize();
        if (outputAnnotation.codeGenerationType().generateImplementationCode()) {
            interceptor = MethodDelegation.to(OutputMappingInterceptor.class);
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }
        return builder.defineMethod(
                        outputMethod.getName(), outputMethod.getReturnType(), Visibility.PUBLIC)
                .withParameters(parameterTypes)
                .throwing(IOException.class)
                .intercept(interceptor);
    }

    private static <T> ReceiverTypeDefinition<T> buildImplementationInputMethod(
            final ReceiverTypeDefinition<T> builder, final StorageMethodInfo inputMethodInfo) {
        final var inputMethod = inputMethodInfo.storageMethod();
        final var parameters = Arrays.stream(inputMethod.getParameters()).toList();
        final var parameterTypes =
                parameters.stream().map(Parameter::getParameterizedType).toList();
        final Implementation interceptor;
        final var inputAnnotation =
                inputMethodInfo.storageMethodAnnotations().get(InputMapping.class).synthesize();
        if (inputAnnotation.codeGenerationType().generateImplementationCode()) {
            interceptor = MethodDelegation.to(InputMappingInterceptor.class);
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }
        return builder.defineMethod(
                        inputMethod.getName(), inputMethod.getReturnType(), Visibility.PUBLIC)
                .withParameters(parameterTypes)
                .throwing(IOException.class)
                .intercept(interceptor);
    }

    private static <T> ReceiverTypeDefinition<T> buildImplementationListMethod(
            final ReceiverTypeDefinition<T> builder, final StorageMethodInfo listMethodInfo) {
        final var listMethod = listMethodInfo.storageMethod();
        final var parameters = Arrays.stream(listMethod.getParameters()).toList();
        final var parameterTypes =
                parameters.stream().map(Parameter::getParameterizedType).toList();
        final Implementation interceptor;
        final var listAnnotation =
                listMethodInfo.storageMethodAnnotations().get(ListMapping.class).synthesize();
        if (listAnnotation.codeGenerationType().generateImplementationCode()) {
            interceptor = MethodDelegation.to(ListMappingInterceptor.class);
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }
        return builder.defineMethod(
                        listMethod.getName(), listMethod.getReturnType(), Visibility.PUBLIC)
                .withParameters(parameterTypes)
                .throwing(IOException.class)
                .intercept(interceptor);
    }

    private static <T> ReceiverTypeDefinition<T> buildImplementationDeleteMethod(
            final ReceiverTypeDefinition<T> builder, final StorageMethodInfo deleteMethodInfo) {
        final var deleteMethod = deleteMethodInfo.storageMethod();
        final var parameters = Arrays.stream(deleteMethod.getParameters()).toList();
        final var parameterTypes =
                parameters.stream().map(Parameter::getParameterizedType).toList();
        final Implementation interceptor;
        final var deleteAnnotation =
                deleteMethodInfo.storageMethodAnnotations().get(DeleteMapping.class).synthesize();
        if (deleteAnnotation.codeGenerationType().generateImplementationCode()) {
            interceptor = MethodDelegation.to(DeleteMappingInterceptor.class);
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }
        return builder.defineMethod(
                        deleteMethod.getName(), deleteMethod.getReturnType(), Visibility.PUBLIC)
                .withParameters(parameterTypes)
                .throwing(IOException.class)
                .intercept(interceptor);
    }

    protected static Set<String> getBasePackages(
            final EnableStorages enableStoragesAnnotation, final String enableStoragesClassName) {
        final var basePackagesAttribute = enableStoragesAnnotation.basePackages();
        final var basePackageClassesAttribute = enableStoragesAnnotation.basePackageClasses();

        if (ArrayUtils.isEmpty(basePackagesAttribute)
                && ArrayUtils.isEmpty(basePackageClassesAttribute)) {
            // fallback to the package of the Annotation if none of the above attributes is set
            return Set.of(ClassUtils.getPackageName(enableStoragesClassName));
        }

        return Stream.concat(
                        Arrays.stream(basePackageClassesAttribute).map(ClassUtils::getPackageName),
                        Arrays.stream(basePackagesAttribute).filter(StringUtils::isNotBlank))
                .collect(Collectors.toSet());
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
                    storageClass.isInterface(), "@Storage can only be specified on an interface");

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
                        storageInterface,
                        generatedImplementationClass,
                        "implementation");
            }
        }

        if (storageAnnotation.codeGenerationType().generateDocumentationCode()) {
            try (final var generatedDocumentationClass =
                    generateDocumentationClass(
                            storageInterface, mergedAnnotationsSearch, storageAnnotation)) {
                registerBeanForGeneratedClass(
                        beanFactory,
                        storageInterface,
                        generatedDocumentationClass,
                        "documentation");
            }
        }
    }

    private <T> void registerBeanForGeneratedClass(
            final DefaultListableBeanFactory beanFactory,
            final Class<T> storageInterface,
            final DynamicType.Unloaded<T> generatedClass,
            final String storageBeanType) {

        // TODO remove below, only for debugging purposes
        final var tempFile = Path.of("/tmp/bytebuddy");
        try {
            generatedClass.saveIn(tempFile.toFile());
        } catch (final IOException e) {
            throw new WrappedException(e);
        }
        logger.info(
                "saved at {}/{}.class", tempFile, generatedClass.getTypeDescription().getName());
        // TODO remove above, only for debugging purposes

        final var storageClass =
                generatedClass
                        .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                        .getLoaded();
        final var beanDefinitionBuilder =
                BeanDefinitionBuilder.rootBeanDefinition(storageClass)
                        .setAutowireMode(AUTOWIRE_CONSTRUCTOR);
        final var beanDefinition = beanDefinitionBuilder.getBeanDefinition();

        final var beanName =
                CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, storageInterface.getSimpleName());
        beanFactory.registerBeanDefinition(beanName, beanDefinition);
        logger.info(
                "Registered {} bean {} of class {} for storage class {}",
                storageBeanType,
                beanName,
                storageClass.getCanonicalName(),
                storageInterface.getCanonicalName());
    }

    protected ClassPathScanningCandidateComponentProvider getScanner() {
        return new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(
                    final @NotNull AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isIndependent()
                        && !beanDefinition.getMetadata().isAnnotation();
            }
        };
    }

    @Override
    public void setEnvironment(final @NotNull Environment environment) {
        this.environment = environment;
    }

    private record StorageMethodInfo(
            Method storageMethod,
            MergedAnnotations storageMethodAnnotations,
            Class<? extends Annotation> mappingAnnotationType) {}
}
