package ch.ubique.backend.storage.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import org.slf4j.event.Level;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * This is just a meta annotation. The annotations to use in a {@link Storage} class are in the same
 * package. These are:
 *
 * <ul>
 *   <li>{@link InputMapping}
 *   <li>{@link OutputMapping}
 *   <li>{@link DeleteMapping}
 *   <li>{@link ExistsMapping}
 *   <li>{@link ListMapping}
 * </ul>
 */
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
@Documented
@Inherited
public @interface StorageMapping {

    /**
     * This is an alias for {@link #path}. For example, {@code @StorageMapping("foo")} is equivalent
     * to {@code @StorageMapping(path="foo")}.
     */
    @AliasFor("path")
    String value() default "";

    /**
     * The path mapping URIs (e.g. {@code "file.json"}).
     *
     * <p>Cannot start with a `/` and startup will fail if it does.
     *
     * <p><strong>NOTE</strong>: A handler method that is not mapped to any path explicitly is
     * invalid.
     */
    @AliasFor("value")
    String path() default "";

    /**
     * Set to {@link CodeGenerationType#IMPLEMENTATION} or {@link CodeGenerationType#DOCUMENTATION}
     * to generate only implementation code or documentation code, respectively.
     */
    CodeGenerationType codeGenerationType() default CodeGenerationType.ALL;

    /**
     * Specify the log level as a single-element array. We use an array to have {@code {}} emulate
     * null. Passing an array with more than one element results in an exception at application
     * startup.
     */
    Level[] logLevel() default {};
}
