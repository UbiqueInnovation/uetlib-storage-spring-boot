package ch.ubique.backend.storage.annotations.interceptors;

import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public final class OnlyDocumentationInterceptor {
    private OnlyDocumentationInterceptor() {}

    @RuntimeType
    @SuppressWarnings("unused")
    public static Object intercept() {
        return ResponseEntity.of(
                        ProblemDetail.forStatusAndDetail(
                                HttpStatus.BAD_REQUEST,
                                "this storage method exists only to document blob storage endpoints"
                                        + " and must not be called"))
                .build();
    }
}
