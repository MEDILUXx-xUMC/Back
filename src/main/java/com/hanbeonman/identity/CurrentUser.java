// 서버 세션에서 현재 사용자를 확인한다.
package com.hanbeonman.identity;

import com.hanbeonman.common.http.Requests;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final JdbcClient jdbc;

    CurrentUser(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String require(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ") && authorization.length() <= 128) {
            return jdbc.sql("SELECT user_id FROM user_sessions WHERE token_hash = ? AND expires_at > now()")
                    .param(hash(authorization.substring(7))).query(String.class).optional()
                    .orElseThrow(CurrentUser::unauthorized);
        }
        if (authorization == null && request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }
        throw unauthorized();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static RuntimeException unauthorized() {
        return Requests.error(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인해 주세요.");
    }
}
