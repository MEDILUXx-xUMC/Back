// 거래 참여자, 역할과 현재 상태를 확인한다.
package com.hanbeonman.trade;

import com.hanbeonman.common.http.Requests;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class TradeAccess {
    private final JdbcClient jdbc;

    TradeAccess(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> require(String user, UUID trade, boolean lock) {
        String sql = """
                SELECT t.*,q.rental_days,q.deposit_amount,q.charge_amount,p.track,p.title,p.thumbnail_url,p.category_id
                FROM trades t JOIN trade_quotes q ON t.quote_id=q.id JOIN products p ON p.id=t.product_id
                WHERE t.id=? AND (t.owner_id=? OR t.requester_id=?)
                """;
        return jdbc.sql(lock ? sql + " FOR UPDATE OF t" : sql).params(trade,user,user).query(new ColumnMapRowMapper()).optional()
                .orElseThrow(() -> Requests.error(HttpStatus.NOT_FOUND,"TRADE_NOT_FOUND","거래를 찾을 수 없어요."));
    }

    static void role(Map<String,Object> trade, String user, String column) {
        if (!user.equals(trade.get(column))) {
            throw Requests.error(HttpStatus.FORBIDDEN,"FORBIDDEN","이 작업을 수행할 권한이 없어요.");
        }
    }
}
