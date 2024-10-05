package ch.ubique.backend.storage.invocation;

import static ch.ubique.backend.storage.invocation.InvocationHandlerUtil.*;
import static ch.ubique.backend.storage.logger.LogAtNullableLevel.atLevel;

import ch.ubique.backend.core.utils.ArrayUtils;
import ch.ubique.backend.storage.annotations.ListMapping;
import ch.ubique.backend.storage.builder.StorageMappingInvocationProperties;

import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListMappingInvocationHandler implements StorageMappingInvocationHandler {

    private final Logger logger;

    public ListMappingInvocationHandler(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public Object invoke(
            final StorageMappingInvocationProperties storageMappingInvocationProperties,
            final Method method,
            final Object[] args)
            throws Throwable {
        final var storageClient = storageMappingInvocationProperties.storageClient();
        final var listMapping = findAnnotation(method, ListMapping.class);
        final var returnType = method.getReturnType();
        final var level = ArrayUtils.singletonOrNull(listMapping.logLevel());

        final var basePath = storageMappingInvocationProperties.basePath();

        final var path =
                getPath(
                        method.getParameters(),
                        args,
                        getPathTemplate(basePath, listMapping.path()));

        final var collection = storageClient.list(path, listMapping.recursive());

        if (Collection.class.isAssignableFrom(returnType)) {
            atLevel(logger, level)
                    .log(
                            "listing files from path {} with recursive={}",
                            path,
                            listMapping.recursive());
            return collection;
        }

        if (Stream.class.isAssignableFrom(returnType)) {
            atLevel(logger, level)
                    .log(
                            "listing files from path {} with recursive={} and returning as a"
                                    + " stream",
                            path,
                            listMapping.recursive());
            return collection.stream();
        }

        if (List.class.isAssignableFrom(returnType)) {
            atLevel(logger, level)
                    .log(
                            "listing files from path {} with recursive={} and returning as a list",
                            path,
                            listMapping.recursive());
            return collection.stream().toList();
        }

        if (Set.class.isAssignableFrom(returnType)) {
            atLevel(logger, level)
                    .log(
                            "listing files from path {} with recursive={} and returning as a set",
                            path,
                            listMapping.recursive());
            return collection.stream().collect(Collectors.toUnmodifiableSet());
        }

        throw new IllegalStateException(
                MessageFormat.format(
                        "Returning/Converting the collection to type {0} not (yet) supported",
                        returnType));
    }
}
