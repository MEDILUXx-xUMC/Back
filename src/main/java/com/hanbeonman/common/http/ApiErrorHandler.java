// 요청 오류와 내부 장애를 공통 JSON 형식으로 변환한다.
package com.hanbeonman.common.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Body> handleApiError(ApiException error) {
        return ResponseEntity.status(error.status())
                .body(new Body(error.code(), error.getMessage(), error.fieldErrors()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Body> handleTypeMismatch(MethodArgumentTypeMismatchException error) {
        return handleApiError(ApiException.invalidField(error.getName(), "숫자 형식을 확인해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Body> handleUnexpected(Exception error) {
        if (error instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                    .body(new Body("INVALID_REQUEST", "요청 경로와 형식을 확인해 주세요.", Map.of()));
        }
        log.error("Request processing failed", error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Body("INTERNAL_ERROR", "요청을 처리할 수 없어요.", Map.of()));
    }

    record Body(String code, String message, @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors) {}
}
