package ch.ubique.backend.storage.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * {@link Storage} interface have methods that are all annotated with mapping annotations that are
 * "subtypes" of the {@link StorageMapping} meta annotation. Check the javadoc for more info.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Storage {

    /**
     * You may specify the {@link ch.ubique.backend.storage.StorageClient} explicitly, using a
     * qualifier. If only one {@link ch.ubique.backend.storage.StorageClient} bean is configured in
     * the module, this property does not need to be set.
     */
    String clientQualifier() default "";

    /**
     * You may specify the {@link com.fasterxml.jackson.databind.ObjectMapper} explicitly, using a
     * qualifier. If the storage does not serialize any outputs or if only one {@link
     * ch.ubique.backend.storage.StorageClient} bean is configured in the module, this property does
     * not need to be set.
     */
    String objectMapperQualifier() default "";

    /**
     * @deprecated since v2.5, use {@link #documentationBaseUrl()}
     */
    @AliasFor("documentationBaseUrl")
    String baseUrl() default "";

    /**
     * Most of the time, this corresponds to the blob container name. This value is only used in the
     * documentation and has no impact on the output path.
     */
    @AliasFor("baseUrl")
    String documentationBaseUrl() default "";

    /**
     * Base path for all output mappings (comparable to @RequestMapping at Controller class level).
     *
     * <p>Cannot start with a `/` and startup will fail if it does.
     */
    String basePath() default "";

    /**
     * Set to {@link CodeGenerationType#IMPLEMENTATION} or {@link CodeGenerationType#DOCUMENTATION}
     * to generate only implementation code or documentation code, respectively.
     */
    CodeGenerationType codeGenerationType() default CodeGenerationType.ALL;
}
