package ch.ubique.backend.storage.annotations;



import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@StorageMapping
public @interface ListMapping {
    String path();

    /** Specifies whether subdirectories are also returned */
    boolean recursive() default false;

    CodeGenerationType codeGenerationType() default CodeGenerationType.IMPLEMENTATION;
}
