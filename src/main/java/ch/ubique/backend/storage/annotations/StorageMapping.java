package ch.ubique.backend.storage.annotations;

import java.lang.annotation.*;

@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface StorageMapping {}
