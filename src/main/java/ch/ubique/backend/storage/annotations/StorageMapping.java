package ch.ubique.backend.storage.annotations;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface StorageMapping {

    /**
     * The primary mapping expressed by this annotation.
     * <p>This is an alias for {@link #path}. For example,
     * {@code @StorageMapping("/foo")} is equivalent to
     * {@code @StorageMapping(path="/foo")}.
     * <p><strong>NOTE</strong>: A handler method that is not mapped to any path
     * explicitly is invalid.
     */
    @AliasFor("path")
    String value() default "";

    /**
     * The path mapping URIs (e.g. {@code "file.json"}).
     * <p><strong>NOTE</strong>: A handler method that is not mapped to any path
     * explicitly is invalid.
     */
    @AliasFor("value")
    String path() default "";

    CodeGenerationType codeGenerationType() default CodeGenerationType.ALL;
}
