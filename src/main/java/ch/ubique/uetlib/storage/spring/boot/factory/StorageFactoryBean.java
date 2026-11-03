package ch.ubique.uetlib.storage.spring.boot.factory;

import static java.util.Objects.requireNonNull;

import ch.ubique.uetlib.storage.StorageClient;
import ch.ubique.uetlib.storage.builder.StorageBuilder;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import tools.jackson.databind.ObjectMapper;

public final class StorageFactoryBean<T>
        implements FactoryBean<T>, InitializingBean, BeanFactoryAware {

    private final Class<T> type;

    private @Nullable DefaultListableBeanFactory beanFactory;
    private @Nullable String clientQualifier;
    private @Nullable String objectMapperQualifier;
    private @Nullable String documentationBaseUrl;
    private @Nullable String basePath;

    public StorageFactoryBean(final Class<T> type) {
        this.type = type;
    }

    @Override
    public T getObject() {
        final var bf = requireNonNull(beanFactory);

        final var storageClient =
                StringUtils.isNotBlank(clientQualifier)
                        ? bf.getBean(clientQualifier, StorageClient.class)
                        : bf.getBean(StorageClient.class);
        final var objectMapper =
                StringUtils.isNotBlank(objectMapperQualifier)
                        ? bf.getBean(objectMapperQualifier, ObjectMapper.class)
                        : bf.getBean(ObjectMapper.class);
        return StorageBuilder.builder(type)
                .classLoader(requireNonNull(bf.getBeanClassLoader()))
                .storageClient(storageClient)
                .objectMapper(objectMapper)
                .basePath(requireNonNull(basePath))
                .documentationBaseUrl(requireNonNull(documentationBaseUrl))
                .build();
    }

    @Override
    public Class<?> getObjectType() {
        return type;
    }

    @Override
    public void afterPropertiesSet() {}

    @Override
    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException {
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
