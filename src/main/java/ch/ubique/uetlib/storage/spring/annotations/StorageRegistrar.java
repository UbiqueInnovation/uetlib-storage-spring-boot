package ch.ubique.uetlib.storage.spring.annotations;

import static ch.ubique.uetlib.storage.spring.annotations.utils.EnableAnnotationUtils.classForName;
import static ch.ubique.uetlib.storage.spring.annotations.utils.EnableAnnotationUtils.getBasePackages;
import static java.util.Objects.requireNonNull;

import ch.ubique.uetlib.storage.spring.annotations.utils.EnableAnnotationUtils;
import ch.ubique.uetlib.storage.spring.documentation.builder.StorageDocumentationBuilder;
import ch.ubique.uetlib.storage.spring.factory.StorageFactoryBean;
import com.google.common.base.CaseFormat;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.*;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;

class StorageRegistrar
        implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {

    private static final Logger logger = LoggerFactory.getLogger(StorageRegistrar.class);

    private @Nullable ResourceLoader resourceLoader;
    private @Nullable Environment environment;

    private static String classNameToBeanName(final String storageInterfaceSimpleName) {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, storageInterfaceSimpleName);
    }

    private static Class<?> loadEnableStoragesClass(
            final AnnotationMetadata importingClassMetadata) {
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
                    EnableAnnotationUtils.getScanner(
                            requireNonNull(environment),
                            requireNonNull(resourceLoader),
                            Storage.class);
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
            final AnnotationMetadata importingClassMetadata,
            final BeanDefinitionRegistry registry) {
        registerStorageClients((DefaultListableBeanFactory) registry, importingClassMetadata);
    }

    public void registerStorageClients(
            final DefaultListableBeanFactory beanFactory,
            final AnnotationMetadata importingClassMetadata) {
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
                    beanFactory, storageInterface, storageAnnotation, enableStoragesAnnotation);
        }
    }

    private <T> void registerStorageClient(
            final DefaultListableBeanFactory beanFactory,
            final Class<T> storageInterface,
            final Storage storageAnnotation,
            final EnableStorages enableStoragesAnnotation) {

        if (enableStoragesAnnotation.codeGenerationType().generateImplementationCode()
                && storageAnnotation.codeGenerationType().generateImplementationCode()) {
            final var factoryBean = new StorageFactoryBean<>(storageInterface);
            factoryBean.setClientQualifier(storageAnnotation.clientQualifier());
            factoryBean.setObjectMapperQualifier(storageAnnotation.objectMapperQualifier());
            factoryBean.setDocumentationBaseUrl(storageAnnotation.documentationBaseUrl());
            factoryBean.setBasePath(storageAnnotation.basePath());
            factoryBean.setBeanFactory(beanFactory);
            registerImplementationFactoryBean(beanFactory, storageInterface, factoryBean);
        }

        if (enableStoragesAnnotation.codeGenerationType().generateDocumentationCode()
                && storageAnnotation.codeGenerationType().generateDocumentationCode()) {
            final var generatedDocumentationClass =
                    new StorageDocumentationBuilder<T>(beanFactory, requireNonNull(environment))
                            .buildForDocumentationClass(storageInterface, storageAnnotation);
            registerBeanForGeneratedDocumentationClass(
                    beanFactory, storageInterface, generatedDocumentationClass);
        }
    }

    private <T> void registerImplementationFactoryBean(
            final DefaultListableBeanFactory beanFactory,
            final Class<T> storageInterface,
            final StorageFactoryBean<T> factoryBean) {

        final var storageInterfaceSimpleName = storageInterface.getSimpleName();
        final var storageInterfaceCanonicalName = storageInterface.getCanonicalName();

        final var beanName = classNameToBeanName(storageInterfaceSimpleName);

        final var beanDefinitionBuilder =
                BeanDefinitionBuilder.genericBeanDefinition(
                                storageInterface, factoryBean::getObject)
                        .setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        final var beanDefinition = beanDefinitionBuilder.getBeanDefinition();
        final var holder = new BeanDefinitionHolder(beanDefinition, beanName);
        BeanDefinitionReaderUtils.registerBeanDefinition(holder, beanFactory);

        logger.info(
                "Registered storage bean {} of class {} for storage class {}",
                beanName,
                storageInterfaceCanonicalName,
                storageInterfaceCanonicalName);
    }

    private <T, U> void registerBeanForGeneratedDocumentationClass(
            final DefaultListableBeanFactory beanFactory,
            final Class<T> storageInterface,
            final Class<U> storageClass) {

        final var beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(storageClass);

        final var beanDefinition = beanDefinitionBuilder.getBeanDefinition();

        final var storageInterfaceSimpleName = storageInterface.getSimpleName();
        final var storageInterfaceCanonicalName = storageInterface.getCanonicalName();

        final var beanName = classNameToBeanName(storageInterfaceSimpleName);

        final var documentationBeanName = beanName + "DocumentationController";
        final var documentationBean = beanFactory.createBean(beanDefinition.getBeanClass());
        beanFactory.registerSingleton(documentationBeanName, documentationBean);
        logger.info(
                "Registered storage documentation bean {} of class {} for storage class {}",
                documentationBeanName,
                storageClass.getCanonicalName(),
                storageInterfaceCanonicalName);
    }

    @Override
    public void setResourceLoader(final ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(final Environment environment) {
        this.environment = environment;
    }
}
