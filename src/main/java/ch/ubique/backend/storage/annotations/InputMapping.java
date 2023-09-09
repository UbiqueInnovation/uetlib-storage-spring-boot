package ch.ubique.backend.storage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@StorageMapping
public @interface InputMapping {
    String path();

    String contentType() default "";

    boolean doUncompressIfNeeded() default true;

    /**
     * @deprecated since 1.5, use {@link #codeGenerationType}
     *     <p>{@code generateDocumentation = false} corresponds to {@link #codeGenerationType} =
     *     {@link CodeGenerationType#IMPLEMENTATION}
     */
    @Deprecated(since = "1.5", forRemoval = true)
    boolean generateDocumentation() default true;

    CodeGenerationType codeGenerationType() default CodeGenerationType.ALL;
}
