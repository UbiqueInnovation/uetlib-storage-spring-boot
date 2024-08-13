package ch.ubique.backend.storage.annotations;

import ch.ubique.backend.core.utils.ArrayUtils;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.interceptors.*;

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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.*;
import net.bytebuddy.implementation.MethodCall.WithoutSpecifiedTarget;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageRegistrar
        implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private static final String STORAGE_CLIENT_PARAMETER = "storageClient";
    private static final String OBJECT_MAPPER_PARAMETER = "objectMapper";
    private static final Implementation ONLY_DOCUMENTATION_THROW_INTERCEPTOR =
            MethodDelegation.to(OnlyDocumentationInterceptor.class);

    private static final Logger logger = LoggerFactory.getLogger(StorageRegistrar.class);

    private ApplicationContext applicationContext;
    private Environment environment;

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

    private static @NotNull WithoutSpecifiedTarget getCallSuperConstructor() {
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
        return buildImplementationMethods(
                        builderForMethods, annotatedMethodsByAnnotation, storageInterface)
                .make();
    }

    private static <T> DynamicType.@NotNull Unloaded<Object> generateDocumentationClass(
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
                Stream.of(storageAnnotation.baseUrl(), storageAnnotation.basePath())
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.joining("/", "/", ""));
        final var builder =
                new ByteBuddy()
                        .subclass(Object.class)
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
        return buildDocumentationMethods(builder, annotatedMethodsByAnnotation).make();
    }

    private static <T> DynamicType.Builder<T> buildImplementationMethods(
            final DynamicType.Builder<T> b,
            final Map<Class<? extends Annotation>, List<StorageMethodInfo>>
                    annotatedMethodsByAnnotation,
            final Class<T> storageInterface) {

        final var storageClassLogger = LoggerFactory.getLogger(storageInterface);

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
        final var existsMethodInfos =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(ExistsMapping.class),
                        List.<StorageMethodInfo>of());
        final var listMethodInfos =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(ListMapping.class),
                        List.<StorageMethodInfo>of());
        for (final var outputMethodInfo : outputMethodInfos) {
            builder =
                    buildImplementationOutputMethod(builder, outputMethodInfo, storageClassLogger);
        }
        for (final var inputMethodInfo : inputMethodInfos) {
            builder = buildImplementationInputMethod(builder, inputMethodInfo, storageClassLogger);
        }
        for (final var deleteMethodInfo : deleteMethodInfos) {
            builder =
                    buildImplementationDeleteMethod(builder, deleteMethodInfo, storageClassLogger);
        }
        for (final var existsMethodInfo : existsMethodInfos) {
            builder =
                    buildImplementationExistsMethod(builder, existsMethodInfo, storageClassLogger);
        }
        for (final var listMethodInfo : listMethodInfos) {
            builder = buildImplementationListMethod(builder, listMethodInfo, storageClassLogger);
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
        final var returnType =
                TypeDescription.Generic.Builder.parameterizedType(
                                ResponseEntity.class,
                                methodInfo
                                        .storageMethodAnnotations()
                                        .get(OutputMapping.class)
                                        .synthesize()
                                        .documentedClass())
                        .build();
        final var parameters =
                Arrays.stream(method.getParameters())
                        .filter(parameter -> parameter.isAnnotationPresent(PathVariable.class))
                        .toList();
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
        final var methodAnnotations =
                List.of(
                        AnnotationDescription.Builder.ofType(GetMapping.class)
                                .defineArray("value", "/" + mappingPath)
                                .build());

        return buildMethod(
                builder,
                parameters,
                method,
                returnType,
                ONLY_DOCUMENTATION_THROW_INTERCEPTOR,
                methodAnnotations);
    }

    private static <T> DynamicType.Builder<T> buildImplementationMethod(
            final DynamicType.Builder<T> builder,
            final List<Parameter> parameters,
            final Method method,
            final Implementation interceptor) {
        final var returnType = TypeDescription.Generic.Builder.of(method.getReturnType()).build();
        return buildMethod(builder, parameters, method, returnType, interceptor, List.of());
    }

    private static <T> DynamicType.Builder<T> buildMethod(
            final DynamicType.Builder<T> builder,
            final List<Parameter> parameters,
            final Method method,
            final TypeDescription.Generic returnType,
            final Implementation interceptor,
            final List<AnnotationDescription> methodAnnotations) {
        if (parameters.isEmpty()) {
            return builder.defineMethod(method.getName(), returnType, Visibility.PUBLIC)
                    .throwing(IOException.class)
                    .intercept(interceptor);
        }

        // need to create the first parameter first, then the others in a loop so the type of the
        // builder works out
        final var firstParameter =
                parameters.stream().limit(1).collect(MoreCollectors.onlyElement());
        var parametersBuilder =
                builder.defineMethod(method.getName(), returnType, Visibility.PUBLIC)
                        .withParameter(
                                firstParameter.getParameterizedType(),
                                firstParameter.getName(),
                                Modifier.FINAL)
                        .annotateParameter(firstParameter.getAnnotations());

        for (final var parameter : parameters.stream().skip(1).toList()) {
            parametersBuilder =
                    parametersBuilder
                            .withParameter(
                                    parameter.getParameterizedType(),
                                    parameter.getName(),
                                    Modifier.FINAL)
                            .annotateParameter(parameter.getAnnotations());
        }
        return parametersBuilder
                .throwing(IOException.class)
                .intercept(interceptor)
                .annotateMethod(methodAnnotations);
    }

    private static <T> DynamicType.Builder<T> buildImplementationOutputMethod(
            final DynamicType.Builder<T> builder,
            final StorageMethodInfo outputMethodInfo,
            final Logger storageClassLogger) {
        final var outputMethod = outputMethodInfo.storageMethod();
        final var parameters = Arrays.stream(outputMethod.getParameters()).toList();

        final Implementation interceptor;
        final var outputAnnotation =
                outputMethodInfo.storageMethodAnnotations().get(OutputMapping.class).synthesize();
        if (outputAnnotation.codeGenerationType().generateImplementationCode()) {
            final var level = ArrayUtils.singletonOrNull(outputAnnotation.logLevel());
            interceptor =
                    MethodDelegation.to(new OutputMappingInterceptor(storageClassLogger, level));
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }

        return buildImplementationMethod(builder, parameters, outputMethod, interceptor);
    }

    private static <T> DynamicType.Builder<T> buildImplementationInputMethod(
            final DynamicType.Builder<T> builder,
            final StorageMethodInfo inputMethodInfo,
            final Logger storageClassLogger) {
        final var inputMethod = inputMethodInfo.storageMethod();
        final var parameters = Arrays.stream(inputMethod.getParameters()).toList();

        final Implementation interceptor;
        final var inputAnnotation =
                inputMethodInfo.storageMethodAnnotations().get(InputMapping.class).synthesize();
        if (inputAnnotation.codeGenerationType().generateImplementationCode()) {
            final var level = ArrayUtils.singletonOrNull(inputAnnotation.logLevel());
            interceptor =
                    MethodDelegation.to(new InputMappingInterceptor(storageClassLogger, level));
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }

        return buildImplementationMethod(builder, parameters, inputMethod, interceptor);
    }

    private static <T> DynamicType.Builder<T> buildImplementationListMethod(
            final DynamicType.Builder<T> builder,
            final StorageMethodInfo listMethodInfo,
            final Logger storageClassLogger) {
        final var listMethod = listMethodInfo.storageMethod();
        final var parameters = Arrays.stream(listMethod.getParameters()).toList();

        final Implementation interceptor;
        final var listAnnotation =
                listMethodInfo.storageMethodAnnotations().get(ListMapping.class).synthesize();
        if (listAnnotation.codeGenerationType().generateImplementationCode()) {
            final var level = ArrayUtils.singletonOrNull(listAnnotation.logLevel());
            interceptor =
                    MethodDelegation.to(new ListMappingInterceptor(storageClassLogger, level));
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }

        return buildImplementationMethod(builder, parameters, listMethod, interceptor);
    }

    private static <T> DynamicType.Builder<T> buildImplementationDeleteMethod(
            final DynamicType.Builder<T> builder,
            final StorageMethodInfo deleteMethodInfo,
            final Logger storageClassLogger) {
        final var deleteMethod = deleteMethodInfo.storageMethod();
        final var parameters = Arrays.stream(deleteMethod.getParameters()).toList();

        final Implementation interceptor;
        final var deleteAnnotation =
                deleteMethodInfo.storageMethodAnnotations().get(DeleteMapping.class).synthesize();
        if (deleteAnnotation.codeGenerationType().generateImplementationCode()) {
            final var level = ArrayUtils.singletonOrNull(deleteAnnotation.logLevel());
            interceptor =
                    MethodDelegation.to(new DeleteMappingInterceptor(storageClassLogger, level));
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }

        return buildImplementationMethod(builder, parameters, deleteMethod, interceptor);
    }

    private static <T> DynamicType.Builder<T> buildImplementationExistsMethod(
            final DynamicType.Builder<T> builder,
            final StorageMethodInfo existsMethodInfo,
            final Logger storageClassLogger) {
        final var existsMethod = existsMethodInfo.storageMethod();
        final var parameters = Arrays.stream(existsMethod.getParameters()).toList();

        final Implementation interceptor;
        final var existsAnnotation =
                existsMethodInfo.storageMethodAnnotations().get(ExistsMapping.class).synthesize();
        if (existsAnnotation.codeGenerationType().generateImplementationCode()) {
            final var level = ArrayUtils.singletonOrNull(existsAnnotation.logLevel());
            interceptor =
                    MethodDelegation.to(new ExistsMappingInterceptor(storageClassLogger, level));
        } else {
            interceptor = ONLY_DOCUMENTATION_THROW_INTERCEPTOR;
        }

        return buildImplementationMethod(builder, parameters, existsMethod, interceptor);
    }

    private Set<Class<?>> findAllStorageClasses(
            final Class<?> enableStoragesClass,
            final EnableStorages enableStoragesAnnotation,
            final MergedAnnotations.Search mergedAnnotationsSearch) {
        final var storageClasses =
                EnableAnnotationUtils.findAllClassesBy(
                        enableStoragesAnnotation.storages(),
                        enableStoragesAnnotation.basePackages(),
                        enableStoragesAnnotation.basePackageClasses(),
                        enableStoragesClass.getPackageName(),
                        Storage.class);

        return EnableAnnotationUtils.filterClassesByProfileEnabled(
                environment, mergedAnnotationsSearch, storageClasses);
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
        final var mergedAnnotationsSearch =
                MergedAnnotations.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                        .withEnclosingClasses(
                                clazz ->
                                        !ClassUtils.isInnerClass(clazz)
                                                && !ClassUtils.isStaticClass(clazz));
        final var enableStoragesClass =
                EnableAnnotationUtils.findSingletonNullableEnableClass(
                        applicationContext, EnableStorages.class);

        if (enableStoragesClass == null) {
            logger.info(
                    "No {} annotation found, not registering any {} beans.",
                    EnableStorages.class,
                    Storage.class);
            return;
        }

        final var enableStoragesAnnotation =
                mergedAnnotationsSearch
                        .from(enableStoragesClass)
                        .get(EnableStorages.class)
                        .synthesize();
        final var storageClasses =
                findAllStorageClasses(
                        enableStoragesClass, enableStoragesAnnotation, mergedAnnotationsSearch);

        for (final var storageClass : storageClasses) {
            Assert.isTrue(
                    storageClass.isInterface(),
                    "@Storage can only be specified on an interface, but was specified on "
                            + storageClass);

            registerStorageClient(
                    beanFactory, mergedAnnotationsSearch, storageClass, enableStoragesAnnotation);
        }
    }

    private <T> void registerStorageClient(
            final DefaultListableBeanFactory beanFactory,
            final MergedAnnotations.Search mergedAnnotationsSearch,
            final Class<T> storageInterface,
            final EnableStorages enableStoragesAnnotation) {
        final var mergedStorageClassAnnotations = mergedAnnotationsSearch.from(storageInterface);
        final var storageAnnotation = mergedStorageClassAnnotations.get(Storage.class).synthesize();

        if (enableStoragesAnnotation.codeGenerationType().generateImplementationCode()
                && storageAnnotation.codeGenerationType().generateImplementationCode()) {
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

        if (enableStoragesAnnotation.codeGenerationType().generateDocumentationCode()
                && storageAnnotation.codeGenerationType().generateDocumentationCode()) {
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

        // log the classes to the filesystem of trace logging is enabled :D
        if (logger.isTraceEnabled()) {
            writeClassToFileSystem(generatedClass);
        }

        final var storageClass = generatedClass.load(beanFactory.getBeanClassLoader()).getLoaded();
        final var beanDefinitionBuilder =
                BeanDefinitionBuilder.genericBeanDefinition(storageClass)
                        .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);

        final var beanDefinition = beanDefinitionBuilder.getBeanDefinition();

        final var beanName =
                CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, storageInterfaceSimpleName);

        if (storageBeanType == StorageBeanType.IMPLEMENTATION) {
            final var holder = new BeanDefinitionHolder(beanDefinition, beanName);
            BeanDefinitionReaderUtils.registerBeanDefinition(holder, beanFactory);

            logger.info(
                    "Registered storage bean {} of class {} for storage class {}",
                    beanName,
                    storageClass.getCanonicalName(),
                    storageInterfaceCanonicalName);

            return;
        }

        final var documentationBeanName =
                beanName
                        + CaseFormat.UPPER_UNDERSCORE.to(
                                CaseFormat.UPPER_CAMEL, StorageBeanType.DOCUMENTATION.name());
        final var documentationBean = beanFactory.createBean(beanDefinition.getBeanClass());
        beanFactory.registerSingleton(documentationBeanName, documentationBean);
        logger.info(
                "Registered documentation bean {} of class {} for storage class {}",
                documentationBeanName,
                storageClass.getCanonicalName(),
                storageInterfaceCanonicalName);
    }

    @SuppressWarnings("java:S5443")
    private <T> void writeClassToFileSystem(final DynamicType.Unloaded<T> generatedClass) {
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

    @Override
    public void setApplicationContext(final @NotNull ApplicationContext applicationContext)
            throws BeansException {
        this.applicationContext = applicationContext;
        this.environment = applicationContext.getEnvironment();
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
