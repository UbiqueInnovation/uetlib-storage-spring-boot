package ch.ubique.backend.storage.annotations;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.annotation.Annotation;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EnableAnnotationUtils {

    private static final Logger logger = LoggerFactory.getLogger(EnableAnnotationUtils.class);

    private EnableAnnotationUtils() {}

    private static Reflections getReflectionsForPackages(final String packageIdentifier) {
        return new Reflections(new ConfigurationBuilder().forPackages(packageIdentifier));
    }

    @Nullable
    public static <T extends Annotation> Class<?> findSingletonNullableEnableClass(
            final ApplicationContext applicationContext, final Class<T> clazz) {
        final var enableClass = findEnableClasses(applicationContext, clazz);

        if (enableClass == null) {
            logger.debug("No bean class annotated with {} found in application context", clazz);
        }

        return enableClass;
    }

    @NotNull
    public static <T extends Annotation> Class<?> findSingletonEnableClass(
            final ApplicationContext applicationContext, final Class<T> clazz) {
        final var enableClass = findEnableClasses(applicationContext, clazz);

        if (enableClass != null) {
            return enableClass;
        }

        throw new IllegalStateException(
                MessageFormat.format(
                        "Expected one bean class annotated with {0} in the application context, found none.",
                        clazz));
    }

    @Nullable
    private static <T extends Annotation> Class<?> findEnableClasses(
            final ApplicationContext applicationContext, final Class<T> clazz) {
        final var enableClasses =
                applicationContext.getBeansWithAnnotation(clazz).values().stream()
                        .map(Object::getClass)
                        .toList();

        if (enableClasses.size() <= 1) {
            return enableClasses.stream().findFirst().orElse(null);
        }

        throw new IllegalStateException(
                MessageFormat.format(
                        "Expected at most one bean class annotated with {0} in the application context. Found: {1}",
                        clazz, enableClasses));
    }

    public static Set<Class<?>> findAllClassesBy(
            final Class<?>[] classesFromAnnotation,
            final String[] basePackagesFromAnnotation,
            final Class<?>[] basePackageClassesFromAnnotation,
            final String enableClassPackageName,
            final Class<? extends Annotation> annotationClassToFind) {

        final Set<Class<?>> jobClassesFromAnnotation;
        final Set<String> basePackages;
        if (ArrayUtils.isNotEmpty(classesFromAnnotation)) {
            jobClassesFromAnnotation = Set.of(classesFromAnnotation);
            basePackages = Set.of();
        } else {
            jobClassesFromAnnotation = Set.of();
            basePackages =
                    getBasePackages(
                            basePackagesFromAnnotation,
                            basePackageClassesFromAnnotation,
                            enableClassPackageName);
        }

        final Stream<Class<?>> typesAnnotatedWithStream;
        if (basePackages.isEmpty()) {
            typesAnnotatedWithStream = Stream.of();
        } else {
            final var firstReflections =
                    getReflectionsForPackages(
                            basePackages.stream().limit(1).findFirst().orElseThrow());
            final var reflections =
                    basePackages.stream()
                            .skip(1)
                            .map(EnableAnnotationUtils::getReflectionsForPackages)
                            .reduce(firstReflections, Reflections::merge);
            typesAnnotatedWithStream =
                    reflections.getTypesAnnotatedWith(annotationClassToFind).stream()
                            .filter(cls -> isPartOfAnyPackage(cls, basePackages));
        }

        return Stream.concat(jobClassesFromAnnotation.stream(), typesAnnotatedWithStream)
                .collect(Collectors.toSet());
    }

    private static boolean isPartOfAnyPackage(
            final Class<?> cls, final Collection<String> basePackages) {
        return basePackages.stream().anyMatch(basePackage -> isPartOfPackage(cls, basePackage));
    }

    private static boolean isPartOfPackage(final Class<?> cls, final String basePackage) {
        return cls.getPackageName().startsWith(basePackage);
    }

    private static Set<String> getBasePackages(
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
}
