// 결제수단 조회와 로컬 카드 등록을 제공한다.
package com.hanbeonman.payment;

import com.hanbeonman.common.http.Requests;
import com.hanbeonman.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
class PaymentController {
    private final CurrentUser users;
    private final JdbcClient jdbc;
    private final boolean local;

    PaymentController(CurrentUser users, JdbcClient jdbc, Environment environment) {
        this.users = users;
        this.jdbc = jdbc;
        this.local = environment.acceptsProfiles(Profiles.of("local"));
    }

    @GetMapping("/payment-methods")
    Map<String, Object> methods(HttpServletRequest request) {
        return Map.of("items", jdbc.sql("""
                SELECT id AS "paymentMethodId",brand,last4 FROM payment_methods WHERE user_id=? ORDER BY id
                """).param(users.require(request)).query().listOfRows());
    }

    @PostMapping("/payment-method-registration")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> register(HttpServletRequest request) {
        String user = users.require(request);
        if (!local) {
            throw Requests.error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "결제수단 등록을 사용할 수 없어요.");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO payment_methods VALUES (?,?,'DEMO','5678')").params(id, user).update();
        return Map.of("paymentMethodId", id, "brand", "DEMO", "last4", "5678", "status", "REGISTERED");
    }
}
