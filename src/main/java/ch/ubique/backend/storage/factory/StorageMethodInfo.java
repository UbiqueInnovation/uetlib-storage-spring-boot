package ch.ubique.backend.storage.factory;

import org.springframework.core.annotation.MergedAnnotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public record StorageMethodInfo(
        Method storageMethod,
        MergedAnnotations storageMethodAnnotations,
        Class<? extends Annotation> mappingAnnotationType) {}
