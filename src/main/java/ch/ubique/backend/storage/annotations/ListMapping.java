package ch.ubique.backend.storage.annotations;

import org.slf4j.event.Level;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@StorageMapping
public @interface ListMapping {

    /** Alias for {@link StorageMapping#value}. */
    @AliasFor(annotation = StorageMapping.class)
    String value() default "";

    /** Alias for {@link StorageMapping#path}. */
    @AliasFor(annotation = StorageMapping.class)
    String path() default "";

    /** Specifies whether subdirectories are also returned */
    boolean recursive() default false;

    /** Alias for {@link StorageMapping#codeGenerationType}. */
    @AliasFor(annotation = StorageMapping.class)
    CodeGenerationType codeGenerationType() default CodeGenerationType.IMPLEMENTATION;

    /** Alias for {@link StorageMapping#logLevel}. */
    @AliasFor(annotation = StorageMapping.class)
    Level[] logLevel() default {};
}
