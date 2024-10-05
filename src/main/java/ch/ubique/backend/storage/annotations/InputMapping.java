package ch.ubique.backend.storage.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ch.ubique.backend.storage.invocation.OutputMappingInvocationHandler;

import org.slf4j.event.Level;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Implementations of methods annotated by this function are generated at runtime using byte-buddy.
 * The implementation will call {@link OutputMappingInvocationHandler#invoke}. You can debug this
 * method if there are any issues with this mapping.
 */
@Target(METHOD)
@Retention(RUNTIME)
@StorageMapping
public @interface InputMapping {

    /** Alias for {@link StorageMapping#value}. */
    @AliasFor(annotation = StorageMapping.class)
    String value() default "";

    /** Alias for {@link StorageMapping#path}. */
    @AliasFor(annotation = StorageMapping.class)
    String path() default "";

    String contentType() default "";

    boolean doUncompressIfNeeded() default true;

    /** Alias for {@link StorageMapping#codeGenerationType}. */
    @AliasFor(annotation = StorageMapping.class)
    CodeGenerationType codeGenerationType() default CodeGenerationType.IMPLEMENTATION;

    /** Alias for {@link StorageMapping#logLevel}. */
    @AliasFor(annotation = StorageMapping.class)
    Level[] logLevel() default {};
}
