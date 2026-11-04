package ch.uetlib.storage.spring.boot.documentation.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * This is just a marker interface to mark all RestController that exist only for Swagger
 * documentation purposes. This interface currently does nothing else.
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
@Inherited
public @interface StorageDocumentationRestController {

    /** The storage class that is documented by this controller. */
    Class<?> storage() default Void.class;
}
