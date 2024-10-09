package ch.ubique.backend.storage.factory;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.*;
import ch.ubique.backend.storage.builder.StorageImplementationBuilder;
import ch.ubique.backend.storage.invocation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MoreCollectors;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class StorageFactoryBean<T>
        implements FactoryBean<T>, InitializingBean, BeanFactoryAware {

    private static final Logger logger = LoggerFactory.getLogger(StorageFactoryBean.class);
    private static final MergedAnnotations.Search MERGED_ANNOTATIONS_SEARCH =
            MergedAnnotations.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                    .withEnclosingClasses(
                            clazz ->
                                    !ClassUtils.isInnerClass(clazz)
                                            && !ClassUtils.isStaticClass(clazz));

    private final Class<T> type;

    private DefaultListableBeanFactory beanFactory;
    private String clientQualifier;
    private String objectMapperQualifier;
    private String documentationBaseUrl;
    private String basePath;

    public StorageFactoryBean(final Class<T> type) {
        this.type = type;
    }

    private static Map<Method, StorageMappingInvocationHandler>
            getStorageMethodInvocationByMethodName(final Class<?> storageClass) {

        final var storageMethodsByAnnotation = getStorageMethodsByAnnotation(storageClass);

        final var storageClassLogger = LoggerFactory.getLogger(storageClass);

        final var outputMappingInvocationHandler =
                new OutputMappingInvocationHandler(storageClassLogger);
        final var inputMappingInvocationHandler =
                new InputMappingInvocationHandler(storageClassLogger);
        final var deleteMappingInvocationHandler =
                new DeleteMappingInvocationHandler(storageClassLogger);
        final var listMappingInvocationHandler =
                new ListMappingInvocationHandler(storageClassLogger);
        final var existsMappingInvocationHandler =
                new ExistsMappingInvocationHandler(storageClassLogger);

        final var invocationHandlersByMethodName =
                new HashMap<Method, StorageMappingInvocationHandler>();
        for (final var storageMethod :
                storageMethodsByAnnotation.getOrDefault(OutputMapping.class, List.of())) {
            invocationHandlersByMethodName.put(
                    storageMethod.storageMethod(), outputMappingInvocationHandler);
        }
        for (final var storageMethod :
                storageMethodsByAnnotation.getOrDefault(InputMapping.class, List.of())) {
            invocationHandlersByMethodName.put(
                    storageMethod.storageMethod(), inputMappingInvocationHandler);
        }
        for (final var storageMethod :
                storageMethodsByAnnotation.getOrDefault(DeleteMapping.class, List.of())) {
            invocationHandlersByMethodName.put(
                    storageMethod.storageMethod(), deleteMappingInvocationHandler);
        }
        for (final var storageMethod :
                storageMethodsByAnnotation.getOrDefault(ListMapping.class, List.of())) {
            invocationHandlersByMethodName.put(
                    storageMethod.storageMethod(), listMappingInvocationHandler);
        }
        for (final var storageMethod :
                storageMethodsByAnnotation.getOrDefault(ExistsMapping.class, List.of())) {
            invocationHandlersByMethodName.put(
                    storageMethod.storageMethod(), existsMappingInvocationHandler);
        }
        return invocationHandlersByMethodName;
    }

    private static Map<Class<? extends Annotation>, List<StorageMethodInfo>>
            getStorageMethodsByAnnotation(final Class<?> storageClass) {
        return Arrays.stream(storageClass.getMethods())
                .map(method -> Pair.of(method, MERGED_ANNOTATIONS_SEARCH.from(method)))
                // only those meta-annotated by @StorageMapping
                .filter(
                        methodMethodAnnotationPair ->
                                methodMethodAnnotationPair
                                        .getRight()
                                        .isPresent(StorageMapping.class))
                .map(
                        methodMethodAnnotationPair -> {
                            final var storageMethodInfo =
                                    storageMethodInfo(
                                            methodMethodAnnotationPair.getLeft(),
                                            methodMethodAnnotationPair.getRight());
                            final var mappingPath =
                                    storageMethodInfo
                                            .storageMethodAnnotations()
                                            .get(StorageMapping.class)
                                            .synthesize()
                                            .path();
                            if (!mappingPath.startsWith("/")) {
                                return storageMethodInfo;
                            }

                            final var storageMethod = storageMethodInfo.storageMethod();
                            throw new IllegalArgumentException(
                                    MessageFormat.format(
                                            "''{0}.path()'' cannot start with ''/'' for method {1}::{2}. Got path: ''{3}''",
                                            storageMethodInfo
                                                    .mappingAnnotationType()
                                                    .getSimpleName(),
                                            storageMethod.getDeclaringClass().getSimpleName(),
                                            storageMethod.getName(),
                                            mappingPath));
                        })
                .collect(Collectors.groupingBy(StorageMethodInfo::mappingAnnotationType));
    }

    private static StorageMethodInfo storageMethodInfo(
            final Method method, final MergedAnnotations methodAnnotation) {
        return Arrays.stream(method.getAnnotations())
                .map(
                        annotation ->
                                new StorageMethodInfo(
                                        method, methodAnnotation, annotation.annotationType()))
                // get the annotationType of the annotation that is
                // meta-annotated by @StorageMapping
                .filter(
                        storageMethodInfo ->
                                MERGED_ANNOTATIONS_SEARCH
                                        .from(storageMethodInfo.mappingAnnotationType())
                                        .isPresent(StorageMapping.class))
                .collect(MoreCollectors.onlyElement());
    }

    @Override
    public T getObject() {
        final var storageClient =
                StringUtils.isNotBlank(clientQualifier)
                        ? beanFactory.getBean(clientQualifier, StorageClient.class)
                        : beanFactory.getBean(StorageClient.class);
        final var objectMapper =
                StringUtils.isNotBlank(objectMapperQualifier)
                        ? beanFactory.getBean(objectMapperQualifier, ObjectMapper.class)
                        : beanFactory.getBean(ObjectMapper.class);
        final var storageMethodInvocationByMethodName =
                getStorageMethodInvocationByMethodName(type);
        return new StorageImplementationBuilder<>(type)
                .storageMethodInvocationByMethodName(storageMethodInvocationByMethodName)
                .classLoader(beanFactory.getBeanClassLoader())
                .storageClient(storageClient)
                .objectMapper(objectMapper)
                .basePath(basePath)
                .documentationBaseUrl(documentationBaseUrl)
                .build();
    }

    @Override
    public Class<?> getObjectType() {
        return type;
    }

    @Override
    public void afterPropertiesSet() {}

    @Override
    public void setBeanFactory(@NotNull final BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (DefaultListableBeanFactory) beanFactory;
    }

    public void setClientQualifier(final String clientQualifier) {
        this.clientQualifier = clientQualifier;
    }

    public void setObjectMapperQualifier(final String objectMapperQualifier) {
        this.objectMapperQualifier = objectMapperQualifier;
    }

    public void setDocumentationBaseUrl(final String documentationBaseUrl) {
        this.documentationBaseUrl = documentationBaseUrl;
    }

    public void setBasePath(final String basePath) {
        this.basePath = basePath;
    }
}
