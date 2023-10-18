package ch.ubique.backend.storage.annotations;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@StorageMapping
public @interface OutputMapping {

    /** Alias for {@link StorageMapping#value}. */
    @AliasFor(annotation = StorageMapping.class)
    String value() default "";

    /** Alias for {@link StorageMapping#path}. */
    @AliasFor(annotation = StorageMapping.class)
    String path() default "";

    boolean gZip() default true;

    String contentType() default "";

    Class<?> documentedClass() default Object.class;

    /** Alias for {@link StorageMapping#codeGenerationType}. */
    @AliasFor(annotation = StorageMapping.class)
    CodeGenerationType codeGenerationType() default CodeGenerationType.ALL;

    boolean debugLogging() default false;
}
