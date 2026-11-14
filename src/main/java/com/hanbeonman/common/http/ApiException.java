// 응답으로 전달할 HTTP 상태와 오류 정보를 담는다.
package com.hanbeonman.common.http;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    private ApiException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static ApiException invalidField(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "입력값을 확인해 주세요.", Map.of(field, message));
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }

    Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
