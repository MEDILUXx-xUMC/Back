// 로컬에서 수령 만료와 반납 자동 확인을 처리한다.
package com.hanbeonman.trade;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
class LocalTradeExpiry {
    private final JdbcClient jdbc;

    LocalTradeExpiry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    void applyDeadlines() {
        jdbc.sql("""
                UPDATE trades SET status='CANCELED',payment_status='RELEASED',updated_at=now()
                WHERE status='READY_FOR_PICKUP' AND pickup_deadline_at<=now()
                """).update();
        jdbc.sql("""
                WITH completed AS (
                    UPDATE trades SET status='COMPLETED',payment_status='RELEASED',updated_at=now()
                    WHERE status='RETURN_SUBMITTED' AND returned_at<=now()-interval '48 hours'
                    RETURNING requester_id,product_id
                )
                INSERT INTO category_experiences(user_id,category_id)
                SELECT t.requester_id,p.category_id FROM completed t JOIN products p ON p.id=t.product_id
                ON CONFLICT DO NOTHING
                """).update();
    }
}
