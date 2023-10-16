package ch.ubique.backend.storage.annotations.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MoreCollectors;
import com.pivovarit.function.ThrowingSupplier;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class InterceptorUtil {
    private InterceptorUtil() {}

    static String getPath(
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

    static String getPathTemplate(final String basePath, final String path) {
        return basePath.isBlank() ? path : basePath + "/" + path;
    }

    static ObjectMapper getObjectMapper(final Object thiz, final Object[] allArguments) {
        return Arrays.stream(allArguments)
                // objectMapper can be passed as an argument
                .filter(ObjectMapper.class::isInstance)
                .map(ObjectMapper.class::cast)
                .collect(MoreCollectors.toOptional())
                // or else get the objectMapper from the class
                .orElseGet(
                        ThrowingSupplier.unchecked(
                                () ->
                                        getInaccessibleField(
                                                thiz, "objectMapper", ObjectMapper.class)));
    }

    @SuppressWarnings("java:S3011")
    static <T> T getInaccessibleField(
            final Object thiz, final String fieldName, Class<T> fieldClass)
            throws NoSuchFieldException, IllegalAccessException {
        final var field = thiz.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        final var fieldValue = fieldClass.cast(field.get(thiz));
        field.setAccessible(false);
        return fieldValue;
    }

    static <T extends Annotation, O> T findAnnotation(
            final Class<O> clazz, final Class<T> annotationClass) {
        return Objects.requireNonNull(AnnotationUtils.findAnnotation(clazz, annotationClass));
    }

    static <T extends Annotation> T findAnnotation(
            final Method method, final Class<T> annotationClass) {
        return Objects.requireNonNull(AnnotationUtils.findAnnotation(method, annotationClass));
    }
}
