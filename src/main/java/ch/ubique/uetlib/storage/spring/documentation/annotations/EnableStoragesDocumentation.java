package ch.ubique.uetlib.storage.spring.documentation.annotations;

import static ch.ubique.uetlib.storage.annotations.CodeGenerationType.DOCUMENTATION;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ch.ubique.uetlib.storage.spring.annotations.EnableStorages;
import java.lang.annotation.*;
import org.springframework.core.annotation.AliasFor;

/** Shorthand annotation for {@link EnableStorages} to generate documentation only */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
@Inherited
@EnableStorages(codeGenerationType = DOCUMENTATION)
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
