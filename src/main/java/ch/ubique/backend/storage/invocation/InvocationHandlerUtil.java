package ch.ubique.backend.storage.invocation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MoreCollectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class InvocationHandlerUtil {

    private InvocationHandlerUtil() {}

    static ObjectMapper getObjectMapper(
            final ObjectMapper fallbackObjectMapper, final Object[] args) {
        return Arrays.stream(args)
                // objectMapper can be passed as an argument
                .filter(ObjectMapper.class::isInstance)
                .map(ObjectMapper.class::cast)
                .collect(MoreCollectors.toOptional())
                // or else get the objectMapper from the class
                .orElse(fallbackObjectMapper);
    }

    public static String getPath(
            final Parameter[] parameters, final Object[] allArguments, final String pathTemplate) {
        var replacedPath = pathTemplate;
        for (var i = 0; i < parameters.length; i++) {
            final var parameter = parameters[i];
            final var pathVariableAnnotation =
                    Optional.ofNullable(parameter.getAnnotation(PathVariable.class));
            if (pathVariableAnnotation.isEmpty()) {
                continue;
            }

            final var parameterName = parameter.getName();
            final var pathVariableName =
                    pathVariableAnnotation
                            .map(PathVariable::name)
                            .filter(StringUtils::isNotBlank)
                            .orElse(parameterName);
            final var pathVariableReplaceNameString = "{" + pathVariableName + "}";
            final var parameterValue =
                    parameter.getType().isPrimitive()
                            ? String.valueOf(allArguments[i])
                            : allArguments[i].toString();
            replacedPath = replacedPath.replace(pathVariableReplaceNameString, parameterValue);
        }
        return replacedPath;
    }

    public static String getPathTemplate(final String basePath, final String path) {
        return basePath.isBlank() ? path : basePath + "/" + path;
    }

    public static <T extends Annotation> T findAnnotation(
            final Method method, final Class<T> annotationClass) {
        return Objects.requireNonNull(AnnotationUtils.findAnnotation(method, annotationClass));
    }
}
