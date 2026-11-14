// 견적, 멱등 거래 생성과 상태 전이를 관리한다.
package com.hanbeonman.trade;

import com.hanbeonman.common.http.ApiException;
import com.hanbeonman.common.http.Requests;
import com.hanbeonman.upload.UploadService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TradeService {
    private final JdbcClient jdbc;
    private final TradeAccess access;
    private final UploadService uploads;
    private final boolean local;

    TradeService(JdbcClient jdbc, TradeAccess access, UploadService uploads, Environment environment) {
        this.jdbc = jdbc;
        this.access = access;
        this.uploads = uploads;
        this.local = environment.acceptsProfiles(Profiles.of("local"));
    }

    Map<String,Object> quote(String user, String productId, Integer days) {
        String id = Requests.text("productId", productId, 64);
        var product = jdbc.sql("""
                SELECT p.* FROM products p JOIN neighborhoods n ON p.neighborhood_id=n.id
                JOIN app_users u ON u.id=?
                JOIN neighborhoods center ON center.id=u.verified_neighborhood_id
                WHERE p.id=? AND 6371000 * acos(greatest(-1.0,least(1.0,
                    sin(radians(n.latitude))*sin(radians(center.latitude)) + cos(radians(n.latitude))
                    *cos(radians(center.latitude))*cos(radians(n.longitude-center.longitude))))) <= 1000
                """).params(user,id).query(new ColumnMapRowMapper()).optional()
                .orElseThrow(() -> Requests.error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "제품을 찾을 수 없어요."));
        if (user.equals(product.get("owner_id"))) throw unavailable();
        boolean rental = "RENTAL".equals(product.get("track"));
        if (rental && (days == null || days < 1 || days > 7) || !rental && days != null) {
            throw ApiException.invalidField("rentalDays", "대여 기간은 1일부터 7일까지 선택할 수 있어요.");
        }
        Long deposit = (Long)product.get("deposit_amount");
        long charge;
        long total;
        try {
            charge = rental ? Math.multiplyExact((Long)product.get("daily_fee_amount"), days) : (Long)product.get("sale_price_amount");
            total = Requests.amount("initialTotalAmount", Math.addExact(deposit == null ? 0 : deposit, charge));
        } catch (ArithmeticException error) {
            throw ApiException.invalidField("initialTotalAmount", "금액 범위를 확인해 주세요.");
        }
        UUID quote = UUID.randomUUID();
        OffsetDateTime expires = OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(15);
        jdbc.sql("INSERT INTO trade_quotes VALUES (?,?,?,?,?,?,?)")
                .params(Arrays.asList(quote,id,user,days,deposit,charge,expires)).update();
        Map<String,Object> response = new LinkedHashMap<>(Map.of(
                "quoteId",quote,"track",product.get("track"),"expiresAt",expires,"initialTotalAmount",total));
        if (rental) {
            response.put("depositAmount",deposit);
            response.put("usageFeeAmount",charge);
        } else {
            response.put("salePriceAmount",charge);
        }
        return response;
    }

    @Transactional
    Map<String,Object> create(String user, UUID quote, UUID method, String key) {
        requireLocalPayment();
        // 사용자 잠금으로 동일 요청과 결제수단 재시도를 직렬화한다.
        jdbc.sql("SELECT id FROM app_users WHERE id=? FOR UPDATE").param(user).query(String.class).single();
        var previous = jdbc.sql("SELECT id,quote_id,payment_method_id FROM trades WHERE requester_id=? AND idempotency_key=?")
                .params(user,key).query(new ColumnMapRowMapper()).optional();
        if (previous.isPresent()) {
            var row = previous.get();
            if (!quote.equals(row.get("quote_id")) || !method.equals(row.get("payment_method_id"))) {
                throw Requests.error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","같은 요청 키로 다른 내용을 보낼 수 없어요.");
            }
            return response(access.require(user,(UUID)row.get("id"),false),user);
        }
        boolean pendingConsent = jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM (
                        SELECT DISTINCT ON (id) id,version,required
                        FROM policies ORDER BY id,version::integer DESC
                    ) latest
                    WHERE required AND NOT EXISTS (
                        SELECT 1 FROM user_consents c WHERE c.user_id=?
                          AND c.policy_id=latest.id AND c.version=latest.version)
                )
                """).param(user).query(Boolean.class).single();
        if (pendingConsent) {
            throw Requests.error(HttpStatus.FORBIDDEN, "POLICY_CONSENT_REQUIRED", "필수 정책에 동의해 주세요.");
        }
        var offer = jdbc.sql("""
                SELECT q.product_id,p.owner_id FROM trade_quotes q JOIN products p ON q.product_id=p.id
                WHERE q.id=? AND q.requester_id=? AND q.expires_at>now() FOR UPDATE OF p
                """).params(quote,user).query(new ColumnMapRowMapper()).optional()
                .orElseThrow(() -> Requests.error(HttpStatus.CONFLICT,"QUOTE_EXPIRED","견적을 다시 받아 주세요."));
        boolean available = jdbc.sql("""
                SELECT NOT EXISTS (SELECT 1 FROM trades WHERE quote_id=? OR
                    (product_id=? AND status NOT IN ('REJECTED','CANCELED')
                    AND (status<>'COMPLETED' OR EXISTS (
                        SELECT 1 FROM products p WHERE p.id=trades.product_id AND p.track='SEALED_SALE'))))
                """).params(quote,offer.get("product_id")).query(Boolean.class).single();
        if (!available || user.equals(offer.get("owner_id"))) throw unavailable();
        boolean validMethod = jdbc.sql("SELECT EXISTS(SELECT 1 FROM payment_methods WHERE id=? AND user_id=?)")
                .params(method,user).query(Boolean.class).single();
        if (!validMethod) {
            throw Requests.error(HttpStatus.UNPROCESSABLE_ENTITY,"PAYMENT_HOLD_FAILED","결제수단을 확인해 주세요.");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO trades(id,quote_id,requester_id,owner_id,product_id,payment_method_id,idempotency_key,status,payment_status)
                VALUES (?,?,?,?,?,?,?,'REQUESTED','HELD')
                """).params(id,quote,user,offer.get("owner_id"),offer.get("product_id"),method,key).update();
        return response(access.require(user,id,false),user);
    }

    @Transactional
    Map<String,Object> act(String user, UUID id, String action, Action body) {
        var trade = access.require(user,id,true);
        boolean rental = "RENTAL".equals(trade.get("track"));
        String role = switch (action) {
            case "accept", "reject", "ready", "confirm-return" -> "owner_id";
            default -> "requester_id";
        };
        TradeAccess.role(trade,user,role);
        String expected = switch (action) {
            case "accept","reject","cancel" -> "REQUESTED";
            case "ready" -> "ACCEPTED";
            case "pickup" -> "READY_FOR_PICKUP";
            case "return" -> "IN_USE";
            case "confirm-return" -> "RETURN_SUBMITTED";
            default -> throw Requests.error(HttpStatus.NOT_FOUND,"INVALID_REQUEST","동작을 찾을 수 없어요.");
        };
        if ((action.equals("return") || action.equals("confirm-return")) && !rental) throw invalidState();
        if (!expected.equals(trade.get("status"))) throw invalidState();
        switch (action) {
            case "accept" -> jdbc.sql("""
                    UPDATE trades SET status='ACCEPTED',address=?,instructions=?,updated_at=now() WHERE id=?
                    """).params(Requests.text("address",body.address(),250),
                            body.instructions() == null || body.instructions().isBlank() ? "" : Requests.text("instructions",body.instructions(),500),id).update();
            case "reject","cancel" -> updateStatus(id,action.equals("reject") ? "REJECTED" : "CANCELED","RELEASED");
            case "ready" -> {
                uploads.attach(body.handoffPhotoId(),user,id,null,"HANDOFF");
                uploads.attach(body.conditionPhotoId(),user,id,null,"CONDITION");
                jdbc.sql("UPDATE trades SET status='READY_FOR_PICKUP',pickup_deadline_at=now()+interval '30 minutes',updated_at=now() WHERE id=?")
                        .param(id).update();
            }
            case "pickup" -> {
                if (instant(trade.get("pickup_deadline_at")).isBefore(Instant.now())) throw invalidState();
                uploads.attach(body.pickupPhotoId(),user,id,null,"PICKUP");
                if (rental) {
                    jdbc.sql("UPDATE trades SET status='IN_USE',return_due_at=now()+make_interval(days=>?),updated_at=now() WHERE id=?")
                            .params(trade.get("rental_days"),id).update();
                } else {
                    complete(trade,false);
                }
            }
            case "return" -> {
                uploads.attach(body.returnPhotoId(),user,id,null,"RETURN");
                jdbc.sql("UPDATE trades SET status='RETURN_SUBMITTED',returned_at=now(),updated_at=now() WHERE id=?").param(id).update();
            }
            case "confirm-return" -> complete(trade,true);
            default -> throw invalidState();
        }
        return response(access.require(user,id,false),user);
    }

    @Transactional
    Map<String,Object> dispute(String user, UUID id, String reason, List<String> photos) {
        var trade = access.require(user,id,true);
        if (!Set.of("READY_FOR_PICKUP","IN_USE","RETURN_SUBMITTED").contains(trade.get("status"))) throw invalidState();
        if (photos != null && photos.size() > 5) throw ApiException.invalidField("photoIds","사진은 5장까지 올릴 수 있어요.");
        if (photos != null) photos.forEach(photo -> uploads.attach(photo,user,id,null,"DISPUTE"));
        UUID dispute = UUID.randomUUID();
        jdbc.sql("INSERT INTO trade_disputes VALUES (?,?,?,?)").params(dispute,id,user,Requests.text("reason",reason,1000)).update();
        updateStatus(id,"DISPUTED",(String)trade.get("payment_status"));
        return Map.of("disputeId",dispute,"tradeId",id,"status","OPEN");
    }

    @Transactional
    Map<String,Object> review(String user, UUID id, String text) {
        var trade = access.require(user,id,true);
        TradeAccess.role(trade,user,"requester_id");
        if (!"COMPLETED".equals(trade.get("status"))) throw invalidState();
        return jdbc.sql("""
                INSERT INTO trade_reviews(id,trade_id,body) VALUES (?,?,?) ON CONFLICT (trade_id) DO NOTHING
                RETURNING id AS "reviewId",trade_id AS "tradeId",body AS text,created_at AS "createdAt"
                """).params(UUID.randomUUID(),id,Requests.text("text",text,1000)).query(new ColumnMapRowMapper()).optional()
                .orElseThrow(() -> Requests.error(HttpStatus.CONFLICT,"REVIEW_ALREADY_EXISTS","이미 후기를 작성했어요."));
    }

    private void complete(Map<String,Object> trade, boolean rental) {
        requireLocalPayment();
        updateStatus((UUID)trade.get("id"),"COMPLETED",rental ? "RELEASED" : "CAPTURED");
        jdbc.sql("INSERT INTO category_experiences(user_id,category_id) VALUES (?,?) ON CONFLICT DO NOTHING")
                .params(trade.get("requester_id"),trade.get("category_id")).update();
    }

    private void updateStatus(UUID id, String status, String payment) {
        requireLocalPayment();
        jdbc.sql("UPDATE trades SET status=?,payment_status=?,updated_at=now() WHERE id=?").params(status,payment,id).update();
    }

    private void requireLocalPayment() {
        if (!local) throw Requests.error(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","결제 연동을 사용할 수 없어요.");
    }

    static Map<String,Object> response(Map<String,Object> row, String user) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("tradeId",row.get("id"));
        result.put("productId",row.get("product_id"));
        result.put("productTitle",row.get("title"));
        result.put("thumbnailUrl",row.get("thumbnail_url"));
        result.put("track",row.get("track"));
        result.put("status",row.get("status"));
        result.put("currentUserRole",user.equals(row.get("owner_id")) ? "OWNER" : "REQUESTER");
        result.put("createdAt",instant(row.get("created_at")));
        if ("RENTAL".equals(row.get("track"))) {
            result.put("rentalDays",row.get("rental_days"));
            result.put("returnDueAt",instant(row.get("return_due_at")));
            result.put("depositAmount",row.get("deposit_amount"));
            result.put("usageFeeAmount",row.get("charge_amount"));
        } else {
            result.put("salePriceAmount",row.get("charge_amount"));
        }
        return result;
    }

    static Instant instant(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return (Instant)value;
    }

    static RuntimeException invalidState() {
        return Requests.error(HttpStatus.CONFLICT,"INVALID_TRADE_STATE","현재 거래 상태에서 진행할 수 없어요.");
    }

    private RuntimeException unavailable() {
        return Requests.error(HttpStatus.CONFLICT,"PRODUCT_UNAVAILABLE","현재 거래할 수 없는 제품이에요.");
    }

    record Action(String address, String instructions, String handoffPhotoId, String conditionPhotoId,
                  String pickupPhotoId, String returnPhotoId, String reason) {
        static final Action EMPTY = new Action(null,null,null,null,null,null,null);
    }
}
