package ch.ubique.backend.storage.spring.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ch.ubique.backend.storage.annotations.CodeGenerationType;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Place this annotation on any class in a package that matches (or is prefixed by) your maven
 * artifact group id. This annotation is only discovered in a package below the group id.
 *
 * <p>The package 'ch.ubique.backend' is always included.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Documented
@Import(StorageRegistrar.class)
public @interface EnableStorages {

    /**
     * Alias for {@link #basePackages()}
     * @return the array of 'basePackages'.
     */
    @AliasFor("basePackages")
    String[] value() default {};

    /**
     * <p>Base packages to scan for annotated components.
     *
     * <p>Use {@link #basePackageClasses()} for a type-safe alternative to String-based package
     * names.
     *
     * <p>Alias for {@link #value()}
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
     * @return array of Storage classes
     */
    Class<?>[] storages() default {};

    /**
     * Set to {@link CodeGenerationType#IMPLEMENTATION} or {@link CodeGenerationType#DOCUMENTATION}
     * to generate only implementation code or documentation code, respectively.
     */
    CodeGenerationType codeGenerationType() default CodeGenerationType.ALL;
}
