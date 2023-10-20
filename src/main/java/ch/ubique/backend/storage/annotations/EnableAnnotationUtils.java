package ch.ubique.backend.storage.annotations;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EnableAnnotationUtils {

    private static final Logger logger = LoggerFactory.getLogger(EnableAnnotationUtils.class);

    private EnableAnnotationUtils() {}

    public static <T extends Annotation> Class<?> findEnableClass(
            final String buildPropertiesGroup, final Class<T> clazz) {
        return CollectionUtils.extractSingleton(
                new Reflections(buildPropertiesGroup).getTypesAnnotatedWith(clazz));
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

        final var reflections =
                basePackages.stream()
                        .map(Reflections::new)
                        .reduce(new Reflections("ch.ubique.backend"), Reflections::merge);

        return Stream.concat(
                        jobClassesFromAnnotation.stream(),
                        reflections.getTypesAnnotatedWith(annotationClassToFind).stream())
                .collect(Collectors.toSet());
    }

    public static Set<Class<?>> findAllRegisteredJobClasses(final String buildPropertiesGroup) {
        logger.debug("Scanning for EnableJobs-annotated class in {}", buildPropertiesGroup);
        final var reflections =
                new Reflections(new ConfigurationBuilder().forPackage(buildPropertiesGroup));
        final var reflectionResult = reflections.getTypesAnnotatedWith(EnableJobs.class);

        if (CollectionUtils.isEmpty(reflectionResult)) {
            throw new IllegalArgumentException(
                    "No EnableJobs-annotated class found in %s".formatted(buildPropertiesGroup));
        } else if (reflectionResult.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple EnableJobs-annotated classes found in %s %s, only one is allowed."
                            .formatted(
                                    buildPropertiesGroup,
                                    reflectionResult.stream()
                                            .map(Class::getCanonicalName)
                                            .collect(Collectors.joining(", "))));
        }

        final var enableJobsClass = CollectionUtils.extractSingleton(reflectionResult);
        final var enableJobsAnnotation = enableJobsClass.getAnnotation(EnableJobs.class);
        logger.info(
                "Scanning for RegisteredJob classes in {} with EnableJobs class {}",
                buildPropertiesGroup,
                enableJobsClass.getCanonicalName());
        return findAllClassesBy(
                enableJobsAnnotation.jobs(),
                enableJobsAnnotation.basePackages(),
                enableJobsAnnotation.basePackageClasses(),
                enableJobsClass.getPackageName(),
                RegisteredJob.class);
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
