package ch.ubique.backend.storage.annotations.utils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EnableAnnotationUtils {

    private EnableAnnotationUtils() {}

    private static Reflections getReflectionsForPackages(final String packageIdentifier) {
        return new Reflections(
                new ConfigurationBuilder()
                        .forPackages(packageIdentifier)
                        .setScanners(Scanners.TypesAnnotated)
                        .setExpandSuperTypes(false));
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
                    reflections.getTypesAnnotatedWith(annotationClassToFind, true).stream()
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

    public static Set<Class<?>> filterClassesByProfileEnabled(
            final Environment environment,
            final MergedAnnotations.Search mergedAnnotationsSearch,
            final Set<Class<?>> storageClasses) {
        return storageClasses.stream()
                .filter(
                        storageClass -> {
                            final var mergedAnnotations =
                                    mergedAnnotationsSearch.from(storageClass);
                            final var isProfileAnnotationPresent =
                                    mergedAnnotations.isPresent(Profile.class);
                            if (!isProfileAnnotationPresent) {
                                // no profile annotation -> always instantiate this storage
                                return true;
                            }
                            final var profileAnnotation =
                                    mergedAnnotations.get(Profile.class).synthesize();
                            return environment.matchesProfiles(profileAnnotation.value());
                        })
                .collect(Collectors.toUnmodifiableSet());
    }
}
