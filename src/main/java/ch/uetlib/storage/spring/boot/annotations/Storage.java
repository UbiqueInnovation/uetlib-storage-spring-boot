package ch.uetlib.storage.spring.boot.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import ch.uetlib.storage.annotations.CodeGenerationType;

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
     * You may specify the {@link ch.uetlib.storage.StorageClient} explicitly, using a qualifier. If
     * only one {@link ch.uetlib.storage.StorageClient} bean is configured in the module, this
     * property does not need to be set.
     */
    String clientQualifier() default "";

    /**
     * You may specify the {@link tools.jackson.databind.ObjectMapper} explicitly, using a
     * qualifier. If the storage does not serialize any outputs or if only one {@link
     * ch.uetlib.storage.StorageClient} bean is configured in the module, this property does not
     * need to be set.
     */
    String objectMapperQualifier() default "";

    /**
     * Most of the time, this corresponds to the blob container name. This value is only used in the
     * documentation and has no impact on the output path.
     * <li>Cannot start with a {@code /} and startup will fail if it does.
     * <li>This and {@link Storage#basePath()} cannot both be blank.
     */
    String documentationBaseUrl() default "insert-storage-base-url-here";

    /**
     * Base path for all output mappings (comparable to @RequestMapping at Controller class level).
     * <li>Cannot start with a {@code /} and startup will fail if it does.
     * <li>This and {@link Storage#documentationBaseUrl()} cannot both be blank.
     */
    String basePath() default "";

    /**
     * Set to {@link CodeGenerationType#IMPLEMENTATION} or {@link CodeGenerationType#DOCUMENTATION}
     * to generate only implementation code or documentation code, respectively.
     */
    CodeGenerationType codeGenerationType() default CodeGenerationType.ALL;
}
