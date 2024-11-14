package ch.ubique.backend.storage.spring.factory;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.builder.StorageBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public final class StorageFactoryBean<T>
        implements FactoryBean<T>, InitializingBean, BeanFactoryAware {

    private static final Logger logger = LoggerFactory.getLogger(StorageFactoryBean.class);

    private final Class<T> type;

    private DefaultListableBeanFactory beanFactory;
    private String clientQualifier;
    private String objectMapperQualifier;
    private String documentationBaseUrl;
    private String basePath;

    public StorageFactoryBean(final Class<T> type) {
        this.type = type;
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
        return StorageBuilder.builder(type)
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
