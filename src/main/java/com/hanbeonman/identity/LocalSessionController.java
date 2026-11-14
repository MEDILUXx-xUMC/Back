// 로컬 전용 사용자로 개발 세션을 발급한다.
package com.hanbeonman.identity;

import com.hanbeonman.common.http.Requests;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
class LocalSessionController {
    private static final Set<String> USERS = Set.of("demo-borrower", "demo-owner");
    private final JdbcClient jdbc;
    private final SecureRandom random = new SecureRandom();

    LocalSessionController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/api/v1/auth/session")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> create(@RequestBody Login body) {
        if (body.userId() == null || !USERS.contains(body.userId())) {
            throw Requests.error(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "개발 사용자 ID를 확인해 주세요.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("INSERT INTO user_sessions VALUES (?, ?, now() + interval '8 hours')")
                .param(CurrentUser.hash(token)).param(body.userId()).update();
        return Map.of("accessToken", token, "tokenType", "Bearer", "expiresIn", 28800);
    }

    record Login(String userId) {}
}
