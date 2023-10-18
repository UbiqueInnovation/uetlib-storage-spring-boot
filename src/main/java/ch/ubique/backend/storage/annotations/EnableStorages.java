package ch.ubique.backend.storage.annotations;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Place this annotation on any class in a package that matches (or is prefixed by) your maven
 * artifact group id. This annotation is only discovered in a package below the group id.
 *
 * <p>The package 'ch.ubique.backend' is always included.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(StorageRegistrar.class)
public @interface EnableStorages {
    /** Alias for {@link #basePackages} */
    @AliasFor("basePackages")
    String[] value() default {};

    /**
     * Alias for {@link #value}
     *
     * <p>Base packages to scan for annotated components.
     *
     * <p>Use {@link #basePackageClasses()} for a type-safe alternative to String-based package
     * names.
     *
     * @return the array of 'basePackages'.
     */
    @AliasFor("value")
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
     * List of classes annotated with @{@link Storage}. If not empty, disables classpath scanning.
     *
     * @return array of FeignClient classes
     */
    Class<?>[] storages() default {};

    /**
     * Set to {@link CodeGenerationType#IMPLEMENTATION} or {@link CodeGenerationType#DOCUMENTATION}
     * to generate only implementation code or documentation code, respectively.
     */
    CodeGenerationType codeGenerationType() default CodeGenerationType.ALL;
}
