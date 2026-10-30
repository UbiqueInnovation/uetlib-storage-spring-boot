package ch.ubique.uetlib.storage.spring.documentation.interceptors;

import java.lang.reflect.Method;
import net.bytebuddy.implementation.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public final class OnlyDocumentationInterceptor {

    private OnlyDocumentationInterceptor() {}

    @RuntimeType
    @SuppressWarnings("unused")
    public static Object intercept(
            @Origin final Method method,
            @This final Object thiz,
            @AllArguments final Object[] allArguments) {
        return ResponseEntity.of(
                        ProblemDetail.forStatusAndDetail(
                                HttpStatus.BAD_REQUEST,
                                "this storage method exists only to document blob storage endpoints"
                                        + " and must not be called"))
                .build();
    }
}
