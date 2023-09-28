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

    /**
     * @deprecated since 1.5, use {@link #codeGenerationType}
     * 
     * <p>{@code generateDocumentation = false} corresponds to {@link #codeGenerationType} = {@link CodeGenerationType#IMPLEMENTATION}
     */
    @Deprecated(since = "1.5",forRemoval = true)
    boolean generateDocumentation() default true;

    CodeGenerationType codeGenerationType() default CodeGenerationType.ALL;

    boolean debugLogging() default false;
}
