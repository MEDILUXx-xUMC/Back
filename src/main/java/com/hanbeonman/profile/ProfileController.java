// 이용 통계와 카테고리별 첫 경험을 조회한다.
package com.hanbeonman.profile;

import com.hanbeonman.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
class ProfileController {
    private final CurrentUser users;
    private final JdbcClient jdbc;

    ProfileController(CurrentUser users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @GetMapping("/stats")
    Map<String, Object> stats(HttpServletRequest request) {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM category_experiences WHERE user_id=:user) AS "firstExperienceCount",
                       count(*) AS "lentCount", coalesce(sum(q.charge_amount),0) AS "earnedUsageFeeAmount"
                FROM trades t JOIN trade_quotes q ON t.quote_id=q.id
                WHERE t.owner_id=:user AND t.status='COMPLETED' AND q.rental_days IS NOT NULL
                """).param("user", users.require(request)).query().singleRow();
    }

    @GetMapping("/experiences")
    Map<String, Object> experiences(HttpServletRequest request) {
        return Map.of("items", jdbc.sql("""
                SELECT c.id AS "categoryId", c.name, e.first_experienced_at AS "firstExperiencedAt"
                FROM categories c LEFT JOIN category_experiences e ON c.id=e.category_id AND e.user_id=?
                ORDER BY c.name,c.id
                """).param(users.require(request)).query().listOfRows());
    }
}
