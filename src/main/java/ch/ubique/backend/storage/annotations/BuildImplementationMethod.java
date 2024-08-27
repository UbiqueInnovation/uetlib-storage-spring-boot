package ch.ubique.backend.storage.annotations;

import net.bytebuddy.dynamic.DynamicType;

import org.slf4j.Logger;

@FunctionalInterface
interface BuildImplementationMethod<T> {
    DynamicType.Builder<T> buildImplementationMethod(
            final DynamicType.Builder<T> builder,
            final StorageMethodInfo outputMethodInfo,
            final Logger storageClassLogger);
}
