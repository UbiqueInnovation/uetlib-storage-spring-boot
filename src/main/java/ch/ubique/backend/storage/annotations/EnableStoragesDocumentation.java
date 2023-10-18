package ch.ubique.backend.storage.annotations;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/** Shorthand annotation for {@link EnableStorages} to generate documentation only */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@EnableStorages(codeGenerationType = CodeGenerationType.DOCUMENTATION)
public @interface EnableStoragesDocumentation {
    /** Alias for {@link EnableStorages#value}. */
    @AliasFor(annotation = EnableStorages.class)
    String[] value() default {};

    /** Alias for {@link EnableStorages#basePackages}. */
    @AliasFor(annotation = EnableStorages.class)
    String[] basePackages() default {};

    /** Alias for {@link EnableStorages#basePackageClasses}. */
    @AliasFor(annotation = EnableStorages.class)
    Class<?>[] basePackageClasses() default {};

    /** Alias for {@link EnableStorages#storages}. */
    @AliasFor(annotation = EnableStorages.class)
    Class<?>[] storages() default {};
}
