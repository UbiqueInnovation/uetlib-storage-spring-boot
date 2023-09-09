package ch.ubique.backend.storage.annotations;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.interceptors.DeleteMappingInterceptor;
import ch.ubique.backend.storage.annotations.interceptors.InputMappingInterceptor;
import ch.ubique.backend.storage.annotations.interceptors.ListMappingInterceptor;
import ch.ubique.backend.storage.annotations.interceptors.OutputMappingInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.pivovarit.function.exception.WrappedException;

import jakarta.validation.constraints.NotNull;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.*;
import net.bytebuddy.implementation.MethodCall.WithoutSpecifiedTarget;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageClientRegistrar
        implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {

    private static final String STORAGE_CLIENT_PARAMETER = "storageClient";
    private static final String OBJECT_MAPPER_PARAMETER = "objectMapper";
    private static final Implementation ONLY_DOCUMENTATION_THROW_INTERCEPTOR =
            ExceptionMethod.throwing(
                    IllegalCallerException.class,
                    "this storage method exists only to document blob storage endpoints and must not be called");

    private static final Logger logger = LoggerFactory.getLogger(StorageClientRegistrar.class);
    private ResourceLoader resourceLoader;
    private Environment environment;

    private static boolean isStorageMappingMethod(final Method method) {
        return Arrays.stream(method.getAnnotations())
                .map(Annotation::annotationType)
                .anyMatch(
                        annotationType -> annotationType.isAnnotationPresent(StorageMapping.class));
    }

    @NotNull
    private static Map<Class<? extends Annotation>, List<Method>> getStorageMethodsByAnnotation(
            final Class<?> interfaceClass) {
        return Arrays.stream(interfaceClass.getDeclaredMethods())
                .filter(StorageClientRegistrar::isStorageMappingMethod)
                .collect(
                        Collectors.groupingBy(
                                method ->
                                        Arrays.stream(method.getAnnotations())
                                                .map(Annotation::annotationType)
                                                .filter(
                                                        annotationType ->
                                                                annotationType.isAnnotationPresent(
                                                                        StorageMapping.class))
                                                .findFirst()
                                                .orElseThrow()));
    }

    private DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> buildConstructor(
            final DynamicType.Builder.FieldDefinition.Optional.Valuable<?> builder,
            final Storage storageAnnotation) {
        final var shouldInjectObjectMapper =
                Boolean.parseBoolean(String.valueOf(storageAnnotation.injectObjectMapper()));

        final var storageClientQualifierAnnotation =
                AnnotationDescription.Builder.ofType(Qualifier.class)
                        .define("value", storageAnnotation.clientQualifier())
                        .build();
        var constructorBuilder =
                builder.defineConstructor(Visibility.PUBLIC)
                        // parameter 0: storageClient
                        .withParameter(StorageClient.class, STORAGE_CLIENT_PARAMETER)
                        .annotateParameter(storageClientQualifierAnnotation);
        if (shouldInjectObjectMapper) {
            final var objectMapperQualifierAnnotation =
                    AnnotationDescription.Builder.ofType(Qualifier.class)
                            .define("value", storageAnnotation.objectMapperQualifier())
                            .build();
            constructorBuilder =
                    constructorBuilder
                            // parameter 0: objectMapper iff shouldInjectObjectMapper == true
                            .withParameter(ObjectMapper.class, OBJECT_MAPPER_PARAMETER)
                            .annotateParameter(objectMapperQualifierAnnotation);
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

    private DynamicType.@NotNull Unloaded<?> generateClass(
            final AnnotationMetadata annotationMetadata, final String implementationClassName) {
        final Class<?> interfaceClass;
        try {
            interfaceClass = Class.forName(annotationMetadata.getClassName());
        } catch (final ClassNotFoundException e) {
            throw new WrappedException(e);
        }

        final var storageAnnotation = interfaceClass.getAnnotation(Storage.class);
        final var builderForConstructor =
                new ByteBuddy()
                        .subclass(interfaceClass)
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
        final var builder = buildMethods(builderForMethods, interfaceClass);

        // TODO Documentation class

        final var cls = builder.make();
        final var tempFile = Path.of("/tmp/bytebuddy");
        try {
            cls.saveIn(tempFile.toFile());
        } catch (final IOException e) {
            throw new WrappedException(e);
        }
        logger.info("saved at {}/{}.class", tempFile, cls.getTypeDescription().getName());

        return cls;
    }

    private DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> buildMethods(
            final DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> b,
            final Class<?> interfaceClass) {
        final var annotatedMethodsByAnnotation = getStorageMethodsByAnnotation(interfaceClass);
        var builder = b;
        final var outputMethods =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(OutputMapping.class), List.<Method>of());
        final var inputMethods =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(InputMapping.class), List.<Method>of());
        final var deleteMethods =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(DeleteMapping.class), List.<Method>of());
        final var listMethods =
                Objects.requireNonNullElse(
                        annotatedMethodsByAnnotation.get(ListMapping.class), List.<Method>of());
        for (final var outputMethod : outputMethods) {
            builder = buildOutputMethod(builder, outputMethod);
        }
        for (final var inputMethod : inputMethods) {
            builder = buildInputMethod(builder, inputMethod);
        }
        for (final var deleteMethod : deleteMethods) {
            builder = buildDeleteMethod(builder, deleteMethod);
        }
        for (final var listMethod : listMethods) {
            builder = buildListMethod(builder, listMethod);
        }
        return builder;
    }

    private DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> buildOutputMethod(
            final DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> builder,
            final Method outputMethod) {
        final var parameters = Arrays.stream(outputMethod.getParameters()).toList();
        final var parameterTypes =
                parameters.stream().map(Parameter::getParameterizedType).toList();
        final Implementation interceptor;
        final var outputAnnotation = outputMethod.getAnnotation(OutputMapping.class);
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

    private DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> buildInputMethod(
            final DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> builder,
            final Method inputMethod) {
        final var parameters = Arrays.stream(inputMethod.getParameters()).toList();
        final var parameterTypes =
                parameters.stream().map(Parameter::getParameterizedType).toList();
        final Implementation interceptor;
        final var inputAnnotation = inputMethod.getAnnotation(InputMapping.class);
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

    private DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> buildListMethod(
            final DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> builder,
            final Method listMethod) {
        final var parameters = Arrays.stream(listMethod.getParameters()).toList();
        final var parameterTypes =
                parameters.stream().map(Parameter::getParameterizedType).toList();
        final Implementation interceptor;
        final var listAnnotation = listMethod.getAnnotation(ListMapping.class);
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

    private DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> buildDeleteMethod(
            final DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<?> builder,
            final Method deleteMethod) {
        final var parameters = Arrays.stream(deleteMethod.getParameters()).toList();
        final var parameterTypes =
                parameters.stream().map(Parameter::getParameterizedType).toList();
        final Implementation interceptor;
        final var deleteAnnotation = deleteMethod.getAnnotation(DeleteMapping.class);
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

    @Override
    public void registerBeanDefinitions(
            final @NotNull AnnotationMetadata importingClassMetadata,
            final @NotNull BeanDefinitionRegistry registry) {
        registerStorageClients(importingClassMetadata, registry);
    }

    public void registerStorageClients(
            final AnnotationMetadata metadata, final BeanDefinitionRegistry registry) {
        final var candidateComponents = new LinkedHashSet<BeanDefinition>();
        final var attrs =
                Objects.requireNonNull(
                        metadata.getAnnotationAttributes(EnableStorages.class.getName()));

        final var clients = (Class<?>[]) attrs.get("clients");
        if (ArrayUtils.isEmpty(clients)) {
            final var scanner = getScanner();
            scanner.setResourceLoader(resourceLoader);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Storage.class));
            final var basePackages = getBasePackages(metadata);
            for (final var basePackage : basePackages) {
                candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
            }
        } else {
            for (final var clazz : clients) {
                candidateComponents.add(new AnnotatedGenericBeanDefinition(clazz));
            }
        }

        for (final var candidateComponent : candidateComponents) {
            if (candidateComponent instanceof final AnnotatedBeanDefinition beanDefinition) {
                // verify annotated class is an interface
                final var annotationMetadata =
                        (StandardAnnotationMetadata) beanDefinition.getMetadata();
                Assert.isTrue(
                        annotationMetadata.isInterface(),
                        "@Storage can only be specified on an interface");

                registerStorageClient(registry, annotationMetadata);
            }
        }
    }

    private void registerStorageClient(
            final BeanDefinitionRegistry registry,
            final StandardAnnotationMetadata annotationMetadata) {
        final var implementationClassName = annotationMetadata.getClassName() + "Impl";

        try (final var generatedClass =
                generateClass(annotationMetadata, implementationClassName)) {
            final var storageClientClass =
                    generatedClass
                            .load(
                                    getClass().getClassLoader(),
                                    ClassLoadingStrategy.Default.INJECTION)
                            .getLoaded();
            final var beanDefinitionBuilder =
                    BeanDefinitionBuilder.rootBeanDefinition(storageClientClass)
                    /*.addConstructorArgReference(constructorArgReference)*/ ;

            final var beanName =
                    CaseFormat.UPPER_CAMEL.to(
                            CaseFormat.LOWER_CAMEL,
                            annotationMetadata.getIntrospectedClass().getSimpleName());
            final var beanFactory = (DefaultListableBeanFactory) registry;
            beanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());
        }
    }

    protected Set<String> getBasePackages(final AnnotationMetadata importingClassMetadata) {
        final var attributes =
                Objects.requireNonNull(
                        importingClassMetadata.getAnnotationAttributes(
                                EnableStorages.class.getCanonicalName()));

        final var basePackagesAttribute = (String[]) attributes.get("basePackages");
        final var basePackageClassesAttribute = (String[]) attributes.get("basePackageClasses");

        if (ArrayUtils.isEmpty(basePackagesAttribute)
                && ArrayUtils.isEmpty(basePackageClassesAttribute)) {
            // fallback to the package of the Annotation if none of the above attributes is set
            return Set.of(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }

        return Stream.concat(
                        Arrays.stream(basePackageClassesAttribute),
                        Arrays.stream(basePackagesAttribute).filter(StringUtils::isNotBlank))
                .collect(Collectors.toSet());
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
    public void setResourceLoader(final @NotNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(final @NotNull Environment environment) {
        this.environment = environment;
    }
}
