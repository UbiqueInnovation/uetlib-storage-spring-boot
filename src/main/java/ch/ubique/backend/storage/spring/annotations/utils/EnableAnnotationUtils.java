package ch.ubique.backend.storage.spring.annotations.utils;

import com.pivovarit.function.exception.WrappedException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EnableAnnotationUtils {

    private EnableAnnotationUtils() {}

    public static ClassPathScanningCandidateComponentProvider getScanner(
            final Environment environment,
            final ResourceLoader resourceLoader,
            final Class<? extends Annotation> annotationToFind) {
        final var scanner = new AnnotatedTypeScanner(environment);
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotationToFind));
        return scanner;
    }

    public static Set<String> getBasePackages(
            final String[] basePackagesAttribute,
            final Class<?>[] basePackageClassesAttribute,
            final String enableClassPackageName) {
        if (ArrayUtils.isEmpty(basePackagesAttribute)
                && ArrayUtils.isEmpty(basePackageClassesAttribute)) {
            // fallback to the package of the Annotation if none of the above attributes is set
            return Set.of(enableClassPackageName);
        }

        return Stream.concat(
                        Arrays.stream(basePackageClassesAttribute).map(Class::getPackageName),
                        Arrays.stream(basePackagesAttribute).filter(StringUtils::isNotBlank))
                .collect(Collectors.toSet());
    }

    public static Class<?> classForName(final String className) {
        try {
            return Class.forName(className);
        } catch (final ClassNotFoundException e) {
            throw new WrappedException(e);
        }
    }

    private static class AnnotatedTypeScanner extends ClassPathScanningCandidateComponentProvider {

        AnnotatedTypeScanner(final Environment environment) {
            super(false, environment);
        }

        @Override
        protected boolean isCandidateComponent(final AnnotatedBeanDefinition beanDefinition) {
            return beanDefinition.getMetadata().isIndependent()
                    && !beanDefinition.getMetadata().isAnnotation();
        }
    }
}
