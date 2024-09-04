package ch.ubique.backend.storage.builder;

import org.springframework.core.annotation.MergedAnnotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

record StorageMethodInfo(
        Method storageMethod,
        MergedAnnotations storageMethodAnnotations,
        Class<? extends Annotation> mappingAnnotationType) {}
