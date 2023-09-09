package ch.ubique.backend.storage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@StorageMapping
public @interface DeleteMapping {
    String path();

    /**
     * Useful for directories. If the directory is not empty, an exception will be thrown if
     * recursive is set to {@code false}. If recursive is set to {@code true} all files in the
     * directory will be deleted.
     */
    boolean recursive() default false;

    CodeGenerationType codeGenerationType() default CodeGenerationType.IMPLEMENTATION;
}
