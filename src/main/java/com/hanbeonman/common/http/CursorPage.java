// 목록 페이지와 조회 조건에 묶인 커서를 반환한다.
package com.hanbeonman.common.http;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;

public record CursorPage<T>(List<T> items, String nextCursor) {
    public static final int SIZE = 20;

    public static String read(String cursor, String scope) {
        if (cursor == null) return null;
        try {
            if (cursor.length() <= 1024) {
                String token = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                if (token.startsWith(scope + ":") && token.length() > scope.length() + 1) {
                    return token.substring(scope.length() + 1);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // 모든 잘못된 커서는 같은 오류로 반환한다.
        }
        throw invalid();
    }

    public static RuntimeException invalid() {
        return Requests.error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "목록을 처음부터 다시 불러와 주세요.");
    }

    public static <T> CursorPage<T> from(List<T> rows, String scope, Function<T,String> position) {
        var items = rows.stream().limit(SIZE).toList();
        String next = rows.size() <= SIZE ? null : Base64.getUrlEncoder().withoutPadding().encodeToString(
                (scope + ":" + position.apply(items.getLast())).getBytes(StandardCharsets.UTF_8));
        return new CursorPage<>(items, next);
    }
}
