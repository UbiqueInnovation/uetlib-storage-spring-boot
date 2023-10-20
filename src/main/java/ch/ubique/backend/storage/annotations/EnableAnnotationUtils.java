package ch.ubique.backend.storage.annotations;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EnableAnnotationUtils {


    private EnableAnnotationUtils() {}

    public static <T extends Annotation> Class<?> findEnableClass(
            final String buildPropertiesGroup, final Class<T> clazz) {
        return CollectionUtils.extractSingleton(
                new Reflections(buildPropertiesGroup).getTypesAnnotatedWith(clazz));
    }

    public static <T extends Annotation> Set<Class<?>> findEnableClasses(
            final String buildPropertiesGroup, final Class<T> clazz) {
        return new Reflections(buildPropertiesGroup).getTypesAnnotatedWith(clazz);
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
