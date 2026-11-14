// 온보딩, 정책 동의와 동네 인증을 제공한다.
package com.hanbeonman.identity;

import com.hanbeonman.common.http.ApiException;
import com.hanbeonman.common.http.Requests;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class IdentityController {
    private static final String CURRENT_POLICIES = "SELECT DISTINCT ON (id) * FROM policies ORDER BY id, version::integer DESC";
    private final JdbcClient jdbc;
    private final CurrentUser users;

    IdentityController(JdbcClient jdbc, CurrentUser users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @DeleteMapping("/auth/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        users.require(request);
        String token = request.getHeader("Authorization");
        if (token != null) {
            jdbc.sql("DELETE FROM user_sessions WHERE token_hash=?").param(CurrentUser.hash(token.substring(7))).update();
        }
    }

    @GetMapping("/policies")
    Map<String, Object> policies() {
        return Map.of("items", jdbc.sql("""
                SELECT id AS "policyId", version, title, content, required FROM (%s) latest
                """.formatted(CURRENT_POLICIES)).query().listOfRows());
    }

    @PostMapping("/me/consents")
    @Transactional
    Map<String, Object> consent(HttpServletRequest request, @RequestBody ConsentBatch body) {
        String user = users.require(request);
        if (body.consents() == null || body.consents().isEmpty() || body.consents().size() > 20) {
            throw ApiException.invalidField("consents", "동의할 정책을 확인해 주세요.");
        }
        for (Consent item : body.consents()) {
            if (item == null) {
                throw ApiException.invalidField("consents", "정책 정보를 확인해 주세요.");
            }
            String policy = Requests.text("policyId", item.policyId(), 64);
            String version = Requests.text("version", item.version(), 32);
            var current = jdbc.sql("SELECT version FROM (" + CURRENT_POLICIES + ") latest WHERE id=?")
                    .param(policy).query(String.class).optional();
            if (current.isEmpty() || !current.get().equals(version)) {
                throw Requests.error(HttpStatus.CONFLICT, "POLICY_VERSION_CHANGED", "최신 정책을 확인해 주세요.");
            }
            jdbc.sql("INSERT INTO user_consents(user_id,policy_id,version) VALUES (?,?,?) ON CONFLICT DO NOTHING")
                    .params(user, policy, version).update();
        }
        return Map.of("items", jdbc.sql("""
                SELECT policy_id AS "policyId", version, agreed_at AS "agreedAt" FROM user_consents WHERE user_id=?
                """).param(user).query().listOfRows());
    }

    @GetMapping("/neighborhoods")
    Map<String, Object> neighborhoods(HttpServletRequest request, @RequestParam(name = "query") String query) {
        users.require(request);
        return Map.of("items", jdbc.sql("""
                SELECT id AS "neighborhoodId", name AS "neighborhoodName"
                FROM neighborhoods WHERE strpos(name, ?) > 0 ORDER BY name,id LIMIT 20
                """).param(Requests.text("query", query, 80)).query().listOfRows());
    }

    @PostMapping("/me/neighborhood-verifications")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String, Object> verify(HttpServletRequest request, @RequestBody Location body) {
        String user = users.require(request);
        if (body.latitude() == null || body.longitude() == null || !Double.isFinite(body.latitude())
                || !Double.isFinite(body.longitude()) || Math.abs(body.latitude()) > 90 || Math.abs(body.longitude()) > 180) {
            throw ApiException.invalidField("location", "유효한 좌표를 입력해 주세요.");
        }
        String id = Requests.text("neighborhoodId", body.neighborhoodId(), 64);
        var neighborhood = jdbc.sql("""
                SELECT name FROM neighborhoods WHERE id=:id AND
                6371000 * acos(greatest(-1.0, least(1.0,
                    sin(radians(latitude))*sin(radians(:lat)) +
                    cos(radians(latitude))*cos(radians(:lat))*cos(radians(longitude-:lon)
                )))) <= 1000
                """).param("id", id).param("lat", body.latitude()).param("lon", body.longitude())
                .query(String.class).optional().orElseThrow(() ->
                        Requests.error(HttpStatus.UNPROCESSABLE_ENTITY, "NEIGHBORHOOD_VERIFICATION_FAILED", "선택한 동네 안에서 인증해 주세요."));
        jdbc.sql("UPDATE app_users SET verified_neighborhood_id=? WHERE id=?").params(id, user).update();
        return Map.of("neighborhoodId", id, "neighborhoodName", neighborhood, "verifiedAt", java.time.Instant.now(), "radiusMeters", 1000);
    }

    @GetMapping("/me")
    Map<String, Object> profile(HttpServletRequest request) {
        String user = users.require(request);
        return jdbc.sql("""
                SELECT u.id AS "userId", u.display_name AS "displayName", n.name AS "neighborhoodName",
                       CASE WHEN EXISTS (SELECT 1 FROM (%s) p WHERE required AND NOT EXISTS
                           (SELECT 1 FROM user_consents c WHERE c.user_id=u.id AND c.policy_id=p.id AND c.version=p.version))
                           THEN 'CONSENT' WHEN n.id IS NULL THEN 'NEIGHBORHOOD'
                           WHEN NOT EXISTS (SELECT 1 FROM payment_methods m WHERE m.user_id=u.id) THEN 'PAYMENT_METHOD'
                           ELSE 'COMPLETE' END AS "onboardingStep"
                FROM app_users u LEFT JOIN neighborhoods n ON n.id=u.verified_neighborhood_id WHERE u.id=?
                """.formatted(CURRENT_POLICIES)).param(user).query(new ColumnMapRowMapper()).optional()
                .orElseThrow(() -> Requests.error(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인해 주세요."));
    }

    record ConsentBatch(List<Consent> consents) {}
    record Consent(String policyId, String version) {}
    record Location(String neighborhoodId, Double latitude, Double longitude) {}
}
