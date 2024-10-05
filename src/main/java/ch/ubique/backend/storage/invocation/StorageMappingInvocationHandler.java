package ch.ubique.backend.storage.invocation;

import ch.ubique.backend.storage.builder.StorageMappingInvocationProperties;

import java.lang.reflect.Method;

public interface StorageMappingInvocationHandler {
    Object invoke(
            StorageMappingInvocationProperties storageMappingInvocationProperties,
            Method method,
            Object[] args)
            throws Throwable;
}
