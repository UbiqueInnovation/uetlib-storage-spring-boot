package ch.ubique.backend.storage.invocation;

import ch.ubique.backend.storage.builder.StorageMappingInvocationProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public class StorageInvocationHandler implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(StorageInvocationHandler.class);

    private final Map<Method, StorageMappingInvocationHandler> dispatch;
    private final StorageMappingInvocationProperties storageMappingInvocationProperties;

    public StorageInvocationHandler(
            final Map<Method, StorageMappingInvocationHandler> dispatch,
            final StorageMappingInvocationProperties storageMappingInvocationProperties) {
        this.dispatch = dispatch;
        this.storageMappingInvocationProperties = storageMappingInvocationProperties;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
            throws Throwable {
        return switch (method.getName()) {
            case "equals" -> equals(args[0]);
            case "hashCode" -> hashCode();
            case "toString" -> toString();
            default -> {
                logger.debug(
                        "invoking call to method {} to {} with args {}",
                        method.getName(),
                        dispatch.get(method),
                        args);
                yield dispatch.get(method).invoke(storageMappingInvocationProperties, method, args);
            }
        };
    }
}
