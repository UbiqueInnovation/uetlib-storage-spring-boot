package ch.ubique.backend.storage.annotations;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(StorageClientRegistrar.class)
public @interface EnableStorages {
    /**
     * Base packages to scan for annotated components.
     *
     * <p>Use {@link #basePackageClasses()} for a type-safe alternative to String-based package
     * names.
     *
     * @return the array of 'basePackages'.
     */
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()} for specifying the packages to scan for
     * annotated components. The package of each class specified will be scanned.
     *
     * <p>Consider creating a special no-op marker class or interface in each package that serves no
     * purpose other than being referenced by this attribute.
     *
     * @return the array of 'basePackageClasses'.
     */
    Class<?>[] basePackageClasses() default {};

    /**
     * List of classes annotated with @FeignClient. If not empty, disables classpath scanning.
     *
     * @return array of FeignClient classes
     */
    Class<?>[] clients() default {};
}
