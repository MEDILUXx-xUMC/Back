// 견적과 거래 조회, 문고리 및 상태 변경 API를 제공한다.
package com.hanbeonman.trade;

import com.hanbeonman.common.http.ApiException;
import com.hanbeonman.common.http.CursorPage;
import com.hanbeonman.common.http.Requests;
import com.hanbeonman.identity.CurrentUser;
import com.hanbeonman.upload.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class TradeController {
    private final CurrentUser users;
    private final TradeService service;
    private final TradeAccess access;
    private final JdbcClient jdbc;

    TradeController(CurrentUser users, TradeService service, TradeAccess access, JdbcClient jdbc) {
        this.users = users;
        this.service = service;
        this.access = access;
        this.jdbc = jdbc;
    }

    @PostMapping("/trade-quotes")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> quote(HttpServletRequest request, @RequestBody Quote body) {
        return service.quote(users.require(request),body.productId(),body.rentalDays());
    }

    @PostMapping("/trades")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> create(HttpServletRequest request,@RequestBody Create body,
            @RequestHeader(name="Idempotency-Key",required=false) String key) {
        return service.create(users.require(request),Requests.id("quoteId",body.quoteId()),
                Requests.id("paymentMethodId",body.paymentMethodId()),Requests.text("Idempotency-Key",key,128));
    }

    @GetMapping("/trades")
    CursorPage<Map<String,Object>> list(HttpServletRequest request,
            @RequestParam(name="group",defaultValue="ALL") String group,
            @RequestParam(name="role",defaultValue="ALL") String role,
            @RequestParam(name="cursor",required=false) String cursor) {
        String user = users.require(request);
        if (!Set.of("ALL","ACTIVE","CLOSED").contains(group)) throw ApiException.invalidField("group","거래 목록을 확인해 주세요.");
        if (!Set.of("ALL","OWNER","REQUESTER").contains(role)) throw ApiException.invalidField("role","거래 역할을 확인해 주세요.");
        String scope = "trade:" + user + ":" + group + ":" + role;
        String position = CursorPage.read(cursor,scope);
        UUID after = parseCursorId(position);
        var rows = jdbc.sql("""
                SELECT t.*,q.rental_days,q.deposit_amount,q.charge_amount,p.track,p.title,p.thumbnail_url
                FROM trades t JOIN trade_quotes q ON t.quote_id=q.id JOIN products p ON p.id=t.product_id
                WHERE (t.owner_id=:user AND :role<>'REQUESTER' OR t.requester_id=:user AND :role<>'OWNER')
                  AND (:group='ALL' OR (t.status IN ('COMPLETED','REJECTED','CANCELED')) = (:group='CLOSED'))
                  AND (CAST(:after AS uuid) IS NULL OR (t.created_at,t.id) < (
                      SELECT created_at,id FROM trades WHERE id=:after AND (owner_id=:user OR requester_id=:user)))
                ORDER BY t.created_at DESC,t.id DESC LIMIT 21
                """).param("user",user).param("role",role).param("group",group).param("after",after)
                .query().listOfRows().stream().map(row -> TradeService.response(row,user)).toList();
        return CursorPage.from(rows,scope,row -> row.get("tradeId").toString());
    }

    @GetMapping("/trades/{id}")
    Map<String,Object> get(HttpServletRequest request,@PathVariable("id") String id) {
        String user = users.require(request);
        return TradeService.response(access.require(user,tradeId(id),false),user);
    }

    @GetMapping("/trades/{id}/handoff")
    Map<String,Object> handoff(HttpServletRequest request, HttpServletResponse response, @PathVariable("id") String id) {
        var trade = access.require(users.require(request),tradeId(id),false);
        if (!Set.of("ACCEPTED","READY_FOR_PICKUP","IN_USE","RETURN_SUBMITTED").contains(trade.get("status"))) {
            throw Requests.error(HttpStatus.NOT_FOUND,"HANDOFF_NOT_AVAILABLE","문고리 정보를 볼 수 없어요.");
        }
        Map<String,Object> result = new LinkedHashMap<>();
        response.setHeader("Cache-Control", "no-store");
        result.put("address",trade.get("address"));
        result.put("instructions",trade.get("instructions"));
        result.put("pickupDeadlineAt",TradeService.instant(trade.get("pickup_deadline_at")));
        return result;
    }

    @GetMapping("/trades/{id}/photos")
    Map<String,Object> photos(HttpServletRequest request,@PathVariable("id") String id) {
        UUID trade = tradeId(id);
        access.require(users.require(request),trade,false);
        return Map.of("items",jdbc.sql("""
                SELECT id,purpose,created_at FROM uploads WHERE trade_id=? AND attached AND purpose<>'CHAT' ORDER BY created_at,id
                """).param(trade).query((row,index) -> Map.of("photoId",row.getObject("id",UUID.class),
                        "purpose",row.getString("purpose"),"url",UploadService.url(row.getObject("id",UUID.class)),
                        "createdAt",row.getObject("created_at",java.time.OffsetDateTime.class))).list());
    }

    @GetMapping("/trades/{id}/payment")
    Map<String,Object> payment(HttpServletRequest request,@PathVariable("id") String id) {
        var trade = access.require(users.require(request),tradeId(id),false);
        return Map.of("status",trade.get("payment_status"),"updatedAt",TradeService.instant(trade.get("updated_at")));
    }

    @PostMapping("/trades/{id}/{action:accept|reject|ready|pickup|return|confirm-return|cancel}")
    Map<String,Object> action(HttpServletRequest request,@PathVariable("id") String id,@PathVariable("action") String action,
            @RequestBody(required=false) TradeService.Action body) {
        return service.act(users.require(request),tradeId(id),action,body == null ? TradeService.Action.EMPTY : body);
    }

    @PostMapping("/trades/{id}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> dispute(HttpServletRequest request,@PathVariable("id") String id,@RequestBody Dispute body) {
        return service.dispute(users.require(request),tradeId(id),body.reason(),body.photoIds());
    }

    @PostMapping("/trades/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> review(HttpServletRequest request,@PathVariable("id") String id,@RequestBody Review body) {
        return service.review(users.require(request),tradeId(id),body.text());
    }

    private UUID tradeId(String id) { return Requests.id("tradeId",id); }

    private UUID parseCursorId(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw CursorPage.invalid();
        }
    }

    record Quote(String productId,Integer rentalDays) {}
    record Create(String quoteId,String paymentMethodId) {}
    record Dispute(String reason,List<String> photoIds) {}
    record Review(String text) {}
}
