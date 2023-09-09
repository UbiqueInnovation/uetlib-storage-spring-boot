package ch.ubique.backend.storage.annotations.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pivovarit.function.ThrowingSupplier;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Optional;

public final class InterceptorUtil {
    private InterceptorUtil() {}

    static String getPath(
            final Parameter[] parameters, final Object[] allArguments, final String pathTemplate) {
        var replacedPath = pathTemplate;
        for (var i = 0; i < parameters.length; i++) {
            final var parameter = parameters[i];
            final var parameterName = parameter.getName();
            final var pathVariableName =
                    Optional.ofNullable(parameter.getAnnotation(PathVariable.class))
                            .map(PathVariable::name)
                            .orElse(parameterName);
            final var parameterValue =
                    parameter.getType().isPrimitive()
                            ? String.valueOf(allArguments[i])
                            : allArguments[i].toString();
            replacedPath = replacedPath.replace(pathVariableName, parameterValue);
        }
        return replacedPath;
    }

    static String getPathTemplate(final String basePath, final String path) {
        return basePath.isBlank() ? path : basePath + "/" + path;
    }

    static ObjectMapper getObjectMapper(final Object thiz, final Object[] allArguments) {
        return // objectMapper can be passed as an argument
        Arrays.stream(allArguments)
                .filter(ObjectMapper.class::isInstance)
                .map(ObjectMapper.class::cast)
                .findFirst()
                .orElseGet(
                        ThrowingSupplier.unchecked(
                                () ->
                                        // else get the objectMapper from the class
                                        (ObjectMapper)
                                                thiz.getClass()
                                                        .getDeclaredField("objectMapper")
                                                        .get(thiz)));
    }
}
