package ch.ubique.backend.storage.annotations;

import static ch.ubique.backend.storage.annotations.utils.EnableAnnotationUtils.classForName;
import static ch.ubique.backend.storage.annotations.utils.EnableAnnotationUtils.getBasePackages;

import ch.ubique.backend.storage.annotations.utils.EnableAnnotationUtils;
import ch.ubique.backend.storage.builder.StorageBuilder;

import com.google.common.base.CaseFormat;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.*;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

class StorageRegistrar
        implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {

    private static final Logger logger = LoggerFactory.getLogger(StorageRegistrar.class);

    private ResourceLoader resourceLoader;
    private Environment environment;

    private static @NotNull String classNameToBeanName(final String storageInterfaceSimpleName) {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, storageInterfaceSimpleName);
    }

    private static @NotNull Class<?> loadEnableStoragesClass(
            final @NotNull AnnotationMetadata importingClassMetadata) {
        final Class<?> enableStoragesClass;
        try {
            enableStoragesClass = Class.forName(importingClassMetadata.getClassName());
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(
                    MessageFormat.format(
                            "Unable to load class {0}, the class that is annotated with @{1}",
                            importingClassMetadata.getClassName(), EnableStorages.class),
                    e);
        }
        return enableStoragesClass;
    }

    private Set<AnnotatedBeanDefinition> findAllStorageBeanDefinitions(
            final Class<?> enableStoragesClass, final EnableStorages enableStoragesAnnotation) {
        final var candidateComponents = new LinkedHashSet<BeanDefinition>();
        final var storages = enableStoragesAnnotation.storages();
        if (org.apache.commons.lang3.ArrayUtils.isEmpty(storages)) {
            final var scanner =
                    EnableAnnotationUtils.getScanner(environment, resourceLoader, Storage.class);
            final var basePackages =
                    getBasePackages(
                            enableStoragesAnnotation.basePackages(),
                            enableStoragesAnnotation.basePackageClasses(),
                            enableStoragesClass.getPackageName());
            for (final var basePackage : basePackages) {
                candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
            }
        } else {
            for (final var storageClass : storages) {
                candidateComponents.add(new AnnotatedGenericBeanDefinition(storageClass));
            }
        }

        return candidateComponents.stream()
                .filter(AnnotatedBeanDefinition.class::isInstance)
                .map(AnnotatedBeanDefinition.class::cast)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void registerBeanDefinitions(
            final @NotNull AnnotationMetadata importingClassMetadata,
            final @NotNull BeanDefinitionRegistry registry) {
        registerStorageClients((DefaultListableBeanFactory) registry, importingClassMetadata);
    }

    public void registerStorageClients(
            final @NotNull DefaultListableBeanFactory beanFactory,
            final @NotNull AnnotationMetadata importingClassMetadata) {
        final var mergedAnnotationsSearch =
                MergedAnnotations.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                        .withEnclosingClasses(
                                clazz ->
                                        !ClassUtils.isInnerClass(clazz)
                                                && !ClassUtils.isStaticClass(clazz));
        final var enableStoragesClass = loadEnableStoragesClass(importingClassMetadata);

        final var enableStoragesAnnotation =
                importingClassMetadata.getAnnotations().get(EnableStorages.class).synthesize();
        final var storageBeanDefinitions =
                findAllStorageBeanDefinitions(enableStoragesClass, enableStoragesAnnotation);

        for (final var storageBeanDefinition : storageBeanDefinitions) {
            final var storageBeanMetadata = storageBeanDefinition.getMetadata();
            // verify annotated class is an interface
            Assert.isTrue(
                    storageBeanMetadata.isInterface(),
                    "@Storage can only be specified on an interface, but was specified on "
                            + storageBeanMetadata);

            final var storageInterface = classForName(storageBeanMetadata.getClassName());
            final var storageAnnotation =
                    storageBeanMetadata.getAnnotations().get(Storage.class).synthesize();

            registerStorageClient(
                    beanFactory,
                    mergedAnnotationsSearch,
                    storageInterface,
                    storageAnnotation,
                    enableStoragesAnnotation);
        }
    }

    private <T> void registerStorageClient(
            final DefaultListableBeanFactory beanFactory,
            final MergedAnnotations.Search mergedAnnotationsSearch,
            final Class<T> storageInterface,
            final Storage storageAnnotation,
            final EnableStorages enableStoragesAnnotation) {

        if (enableStoragesAnnotation.codeGenerationType().generateImplementationCode()
                && storageAnnotation.codeGenerationType().generateImplementationCode()) {
            final var generatedImplementationClass =
                    new StorageBuilder<T>(beanFactory, environment)
                            .buildForImplementationClass(
                                    storageInterface, mergedAnnotationsSearch, storageAnnotation);
            registerBeanForGeneratedClass(
                    beanFactory,
                    storageInterface,
                    generatedImplementationClass,
                    StorageBeanType.IMPLEMENTATION);
        }

        if (enableStoragesAnnotation.codeGenerationType().generateDocumentationCode()
                && storageAnnotation.codeGenerationType().generateDocumentationCode()) {
            final var generatedDocumentationClass =
                    new StorageBuilder<T>(beanFactory, environment)
                            .buildForDocumentationClass(
                                    storageInterface, mergedAnnotationsSearch, storageAnnotation);
            registerBeanForGeneratedClass(
                    beanFactory,
                    storageInterface,
                    generatedDocumentationClass,
                    StorageBeanType.DOCUMENTATION);
        }
    }

    private <T, U> void registerBeanForGeneratedClass(
            final DefaultListableBeanFactory beanFactory,
            final Class<T> storageInterface,
            final Class<U> storageClass,
            final StorageBeanType storageBeanType) {

        final var beanDefinitionBuilder =
                BeanDefinitionBuilder.genericBeanDefinition(storageClass)
                        .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);

        final var beanDefinition = beanDefinitionBuilder.getBeanDefinition();

        final var storageInterfaceSimpleName = storageInterface.getSimpleName();
        final var storageInterfaceCanonicalName = storageInterface.getCanonicalName();

        final var beanName = classNameToBeanName(storageInterfaceSimpleName);

        switch (storageBeanType) {
            case IMPLEMENTATION -> {
                final var holder = new BeanDefinitionHolder(beanDefinition, beanName);
                BeanDefinitionReaderUtils.registerBeanDefinition(holder, beanFactory);

                logger.info(
                        "Registered storage bean {} of class {} for storage class {}",
                        beanName,
                        storageClass.getCanonicalName(),
                        storageInterfaceCanonicalName);
            }
            case DOCUMENTATION -> {
                final var documentationBeanName =
                        beanName
                                + CaseFormat.UPPER_UNDERSCORE.to(
                                        CaseFormat.UPPER_CAMEL,
                                        StorageBeanType.DOCUMENTATION.name());
                final var documentationBean = beanFactory.createBean(beanDefinition.getBeanClass());
                beanFactory.registerSingleton(documentationBeanName, documentationBean);
                logger.info(
                        "Registered storage documentation bean {} of class {} for storage class {}",
                        documentationBeanName,
                        storageClass.getCanonicalName(),
                        storageInterfaceCanonicalName);
            }
        }
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
