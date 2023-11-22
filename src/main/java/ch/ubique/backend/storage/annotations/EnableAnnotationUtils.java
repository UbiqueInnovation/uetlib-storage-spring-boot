package ch.ubique.backend.storage.annotations;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
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
            final String buildPropertiesGroup, final Class<T> clazz) {
        return findEnableClasses(buildPropertiesGroup, clazz)
                .orElseGet(
                        () -> {
                            logger.debug(
                                    "No class with annotation {} found in package {}",
                                    clazz,
                                    buildPropertiesGroup);
                            return null;
                        });
    }

    @NotNull
    public static <T extends Annotation> Class<?> findSingletonEnableClass(
            final String buildPropertiesGroup, final Class<T> clazz) {
        return findEnableClasses(buildPropertiesGroup, clazz)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        MessageFormat.format(
                                                "Expected one class annotated with {0} in package {1}, found none.",
                                                clazz, buildPropertiesGroup)));
    }

    public static <T extends Annotation> Optional<Class<?>> findEnableClasses(
            final String buildPropertiesGroup, final Class<T> clazz) {
        final var enableClasses =
                getReflectionsForPackages(buildPropertiesGroup).getTypesAnnotatedWith(clazz);

        if (enableClasses.size() <= 1) {
            return enableClasses.stream().findFirst();
        }

        throw new IllegalStateException(
                MessageFormat.format(
                        "Expected at most one class annotated with {0} in package {1}. Found: {2}",
                        clazz, buildPropertiesGroup, enableClasses));
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
