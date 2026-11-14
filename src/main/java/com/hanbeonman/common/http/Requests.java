// 공통 입력 검증과 API 오류 생성을 제공한다.
package com.hanbeonman.common.http;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class Requests {
    private Requests() {}

    public static String text(String field, String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw ApiException.invalidField(field, "값을 입력하고 길이를 확인해 주세요.");
        }
        return value.strip();
    }

    public static UUID id(String field, String value) {
        try {
            return UUID.fromString(text(field, value, 36));
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalidField(field, "ID 형식을 확인해 주세요.");
        }
    }

    public static long amount(String field, Long value) {
        if (value == null || value < 0 || value > 9_007_199_254_740_991L) {
            throw ApiException.invalidField(field, "원 단위 정수를 입력해 주세요.");
        }
        return value;
    }

    public static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
