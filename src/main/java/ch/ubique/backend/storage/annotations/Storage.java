package ch.ubique.backend.storage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Storage {
    String clientQualifier();

    /** Most of the time, this probably includes th blob container path. */
    String baseUrl() default "";

    boolean generateDocumentation() default true;

    /**
     * Package where the documentation controller is created. Defaults to
     * `{package-of-annotated-class}.documentation`.
     */
    String documentationPackage() default "";
}
