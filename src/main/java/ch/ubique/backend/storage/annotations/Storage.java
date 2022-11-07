package ch.ubique.backend.storage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Storage {

    /**
     * You may specify the {@link ch.ubique.backend.storage.StorageClient} explicitly, using a qualifier.
     * If only one {@link ch.ubique.backend.storage.StorageClient} bean is configured in the module,
     * this property does not need to be set.
     */
    String clientQualifier() default "";

    /**
     * Specifies if an object mapper should be injected. Defaults to true.
     * If set to false, a new ObjectMapper is instantiated each time an object is serialized.
     */
    boolean injectObjectMapper() default true;

    /**
     * You may specify the {@link com.fasterxml.jackson.databind.ObjectMapper} explicitly, using a qualifier.
     * This field is ignored if {@code injectObjectMapper} is set to false.
     * If the storage does not serialize any outputs or if only one {@link ch.ubique.backend.storage.StorageClient}
     * bean is configured in the module, this property does not need to be set.
     */
    String objectMapperQualifier() default "";


    /** Most of the time, this probably includes th blob container path. */
    String baseUrl() default "";

    boolean generateDocumentation() default true;

    /**
     * Package where the documentation controller is created. Defaults to
     * `{package-of-annotated-class}.documentation`.
     */
    String documentationPackage() default "";
}
