package ch.ubique.backend.storage.builder;

import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.invocation.StorageInvocationHandler;
import ch.ubique.backend.storage.invocation.StorageMappingInvocationHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class StorageImplementationBuilder<T> {

    private static final Logger logger =
            LoggerFactory.getLogger(StorageImplementationBuilder.class);

    private final Class<T> type;

    private ClassLoader classLoader;
    private StorageClient storageClient;
    private ObjectMapper objectMapper;
    private String basePath;
    private String documentationBaseUrl;
    private Map<Method, StorageMappingInvocationHandler> storageMethodInvocationByMethodName;

    public StorageImplementationBuilder(final Class<T> type) {
        this.type = type;
    }

    public StorageImplementationBuilder<T> classLoader(final ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    public StorageImplementationBuilder<T> storageClient(final StorageClient storageClient) {
        this.storageClient = storageClient;
        return this;
    }

    public StorageImplementationBuilder<T> objectMapper(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        return this;
    }

    public StorageImplementationBuilder<T> basePath(final String basePath) {
        this.basePath = basePath;
        return this;
    }

    public StorageImplementationBuilder<T> documentationBaseUrl(final String documentationBaseUrl) {
        this.documentationBaseUrl = documentationBaseUrl;
        return this;
    }

    public StorageImplementationBuilder<T> storageMethodInvocationByMethodName(
            final Map<Method, StorageMappingInvocationHandler>
                    storageMethodInvocationByMethodName) {
        this.storageMethodInvocationByMethodName = storageMethodInvocationByMethodName;
        return this;
    }

    public T build() {
        final var storageInvocationProperties =
                new StorageMappingInvocationProperties(
                        storageClient, objectMapper, basePath, documentationBaseUrl);
        //noinspection unchecked
        return (T)
                Proxy.newProxyInstance(
                        classLoader,
                        new Class[] {type},
                        new StorageInvocationHandler(
                                storageMethodInvocationByMethodName, storageInvocationProperties));
    }
}
