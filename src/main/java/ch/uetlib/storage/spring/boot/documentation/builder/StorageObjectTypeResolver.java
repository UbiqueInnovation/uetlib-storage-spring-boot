package ch.uetlib.storage.spring.boot.documentation.builder;

import static ch.uetlib.storage.parameter.StorageObjectParameterResolver.getStorageObjectParameterIndex;

import ch.uetlib.storage.output.*;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class StorageObjectTypeResolver {

    private StorageObjectTypeResolver() {}

    public static Type getStorageObjectParameterType(final Method method) {
        final var storageObjectParameterIndex = getStorageObjectParameterIndex(method);
        final var parameterType = method.getGenericParameterTypes()[storageObjectParameterIndex];

        if (byte[].class.equals(parameterType)) {
            return byte[].class;
        }
        if (File.class.equals(parameterType)) {
            return byte[].class;
        }
        if (InputStream.class.equals(parameterType)) {
            return byte[].class;
        }
        if (parameterType instanceof final ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof final Class<?> cls
                && Output.class.isAssignableFrom(cls)) {
            // this case handles the generic subtypes of Output, like JsonOutput<T>
            return getFromParametrizedOutputType(parameterizedType);
        }

        if (parameterType instanceof final Class<?> cls && Output.class.isAssignableFrom(cls)) {
            return getTypeFromOutputType(cls);
        }

        // otherwise it is just the type of the parameter
        return parameterType;
    }

    static Type getTypeFromOutputType(final Class<?> type) {
        if (type.isAssignableFrom(GZipOutput.class)) {
            return Object.class;
        }

        if (type.isAssignableFrom(ByteOutput.class)
                || type.isAssignableFrom(InputStreamOutput.class)
                || type.isAssignableFrom(FileOutput.class)) {
            return byte[].class;
        }

        throw new IllegalStateException(
                "OutputMapping StorageObject type encountered a non-documentable subtype of "
                        + Output.class
                        + ": "
                        + type);
    }

    static Type getFromParametrizedOutputType(final ParameterizedType parameterizedType) {
        final var raw = parameterizedType.getRawType();
        if (raw instanceof final Class<?> rawClass && JsonOutput.class.equals(rawClass)) {
            final var typeArgs = parameterizedType.getActualTypeArguments();
            if (typeArgs.length == 1) {
                return typeArgs[0];
            }

            throw new IllegalStateException(
                    "OutputMapping StorageObject type requires one generic type argument to be"
                            + "a JsonOutput<T> with exactly on generic type argument T (found "
                            + typeArgs.length
                            + " type arguments)");
        }

        throw new IllegalStateException(
                "OutputMapping StorageObject type encountered a non-documentable generic subtype of Output: "
                        + parameterizedType);
    }
}
