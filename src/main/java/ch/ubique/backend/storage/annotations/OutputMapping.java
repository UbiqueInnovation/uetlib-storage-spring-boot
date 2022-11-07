package ch.ubique.backend.storage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OutputMapping {
    String path();

    boolean gZip() default true;

    String contentType() default "";

    Class<?> documentedClass() default Object.class;

    boolean generateDocumentation() default true;
}
