package ch.ubique.backend.storage.annotations;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
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

    /**
     * @deprecated since 1.5, use {@link #codeGenerationType}
     *     <p>{@code generateDocumentation = false} corresponds to {@link #codeGenerationType} =
     *     {@link CodeGenerationType#IMPLEMENTATION}
     */
    @Deprecated(since = "1.5", forRemoval = true)
    boolean generateDocumentation() default true;

    /** Alias for {@link StorageMapping#codeGenerationType}. */
    @AliasFor(annotation = StorageMapping.class)
    CodeGenerationType codeGenerationType() default CodeGenerationType.IMPLEMENTATION;
}
