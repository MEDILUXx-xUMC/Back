// 참여자 채팅 목록, 메시지와 읽음 위치를 처리한다.
package com.hanbeonman.chat;

import com.hanbeonman.common.http.ApiException;
import com.hanbeonman.common.http.CursorPage;
import com.hanbeonman.common.http.Requests;
import com.hanbeonman.identity.CurrentUser;
import com.hanbeonman.trade.TradeAccess;
import com.hanbeonman.upload.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chats")
class ChatController {
    private final CurrentUser users;
    private final JdbcClient jdbc;
    private final TradeAccess trades;
    private final UploadService uploads;

    ChatController(CurrentUser users, JdbcClient jdbc, TradeAccess trades, UploadService uploads) {
        this.users = users;
        this.jdbc = jdbc;
        this.trades = trades;
        this.uploads = uploads;
    }

    @GetMapping
    CursorPage<Map<String,Object>> list(HttpServletRequest request,
            @RequestParam(name="role",defaultValue="ALL") String role,
            @RequestParam(name="cursor",required=false) String cursor) {
        String user = users.require(request);
        if (!Set.of("ALL","OWNER","REQUESTER").contains(role)) throw ApiException.invalidField("role","거래 역할을 확인해 주세요.");
        String scope = "chats:" + user + ":" + role;
        String position = CursorPage.read(cursor,scope);
        UUID after;
        try {
            after = position == null ? null : UUID.fromString(position);
        } catch (IllegalArgumentException error) {
            throw CursorPage.invalid();
        }
        var rows = jdbc.sql("""
                SELECT t.id AS "chatId", t.id AS "tradeId", p.title AS "productTitle",
                       (SELECT count(*) FROM chat_messages m WHERE m.trade_id=t.id AND m.sender_id<>:user
                           AND m.position>coalesce(r.position,0)) AS "unreadCount",
                       m.id,m.sender_id,m.body,m.photo_id,m.created_at,m.position
                FROM trades t JOIN products p ON p.id=t.product_id
                LEFT JOIN chat_reads r ON r.trade_id=t.id AND r.user_id=:user
                LEFT JOIN LATERAL (SELECT * FROM chat_messages cm WHERE cm.trade_id=t.id ORDER BY position DESC LIMIT 1) m ON true
                WHERE (t.owner_id=:user AND :role<>'REQUESTER' OR t.requester_id=:user AND :role<>'OWNER')
                  AND (CAST(:after AS uuid) IS NULL OR (t.created_at,t.id)<(
                       SELECT created_at,id FROM trades WHERE id=:after AND (owner_id=:user OR requester_id=:user)))
                ORDER BY t.created_at DESC,t.id DESC LIMIT 21
                """).param("user",user).param("role",role).param("after",after).query().listOfRows();
        var result = rows.stream().map(row -> {
            Map<String,Object> entry = new LinkedHashMap<>();
            for (String field : List.of("chatId","tradeId","productTitle","unreadCount")) entry.put(field,row.get(field));
            entry.put("lastMessage",row.get("id") == null ? null : message(row));
            return entry;
        }).toList();
        return CursorPage.from(result,scope,row -> row.get("chatId").toString());
    }

    @GetMapping("/{id}/messages")
    CursorPage<Map<String,Object>> messages(HttpServletRequest request,@PathVariable("id") String id,
            @RequestParam(name="cursor",required=false) String cursor) {
        String user = users.require(request);
        UUID chat = chatId(id);
        trades.require(user,chat,false);
        String scope = "messages:" + user + ":" + chat;
        String position = CursorPage.read(cursor,scope);
        long before = Long.MAX_VALUE;
        if (position != null) {
            try {
                before = Long.parseLong(position);
                if (before <= 0) throw CursorPage.invalid();
            } catch (NumberFormatException error) {
                throw CursorPage.invalid();
            }
        }
        var rows = jdbc.sql("SELECT * FROM chat_messages WHERE trade_id=? AND position<? ORDER BY position DESC LIMIT 21")
                .params(chat,before).query().listOfRows();
        var page = CursorPage.from(rows,scope,row -> row.get("position").toString());
        return new CursorPage<>(page.items().stream().map(this::message).toList(),page.nextCursor());
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String,Object> send(HttpServletRequest request,@PathVariable("id") String id,@RequestBody Send body) {
        String user = users.require(request);
        UUID chat = chatId(id);
        trades.require(user,chat,true);
        String key = Requests.text("clientMessageId",body.clientMessageId(),128);
        String kind = Requests.text("kind",body.kind(),16);
        String text = null;
        UUID photo = null;
        switch (kind) {
            case "TEXT" -> text = Requests.text("text",body.text(),1000);
            case "PHOTO" -> photo = Requests.id("photoId",body.photoId());
            default -> throw ApiException.invalidField("kind","메시지 종류를 확인해 주세요.");
        }
        var previous = jdbc.sql("SELECT * FROM chat_messages WHERE trade_id=? AND sender_id=? AND client_message_id=?")
                .params(chat,user,key).query(new ColumnMapRowMapper()).optional();
        if (previous.isPresent()) {
            var row = previous.get();
            if (!Objects.equals(text,row.get("body")) || !Objects.equals(photo,row.get("photo_id"))) {
                throw Requests.error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","같은 요청 키로 다른 내용을 보낼 수 없어요.");
            }
            return message(row);
        }
        if (photo != null) uploads.attach(photo.toString(),user,chat,null,"CHAT");
        return message(jdbc.sql("""
                INSERT INTO chat_messages(id,trade_id,sender_id,client_message_id,body,photo_id)
                VALUES (?,?,?,?,?,?) RETURNING *
                """).params(Arrays.asList(UUID.randomUUID(),chat,user,key,text,photo)).query().singleRow());
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(HttpServletRequest request,@PathVariable("id") String id,@RequestBody Read body) {
        String user = users.require(request);
        UUID chat = chatId(id);
        trades.require(user,chat,false);
        long position = jdbc.sql("SELECT position FROM chat_messages WHERE trade_id=? AND id=?")
                .params(chat,Requests.id("lastReadMessageId",body.lastReadMessageId())).query(Long.class).optional()
                .orElseThrow(() -> ApiException.invalidField("lastReadMessageId","채팅방 메시지를 확인해 주세요."));
        jdbc.sql("""
                INSERT INTO chat_reads(trade_id,user_id,position) VALUES (?,?,?)
                ON CONFLICT (trade_id,user_id) DO UPDATE SET position=greatest(chat_reads.position,excluded.position)
                """).params(chat,user,position).update();
    }

    private Map<String,Object> message(Map<String,Object> row) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("messageId",row.get("id"));
        result.put("senderId",row.get("sender_id"));
        result.put("createdAt",row.get("created_at"));
        if (row.get("photo_id") instanceof UUID photo) {
            result.put("kind","PHOTO");
            result.put("photoId",photo);
            result.put("photoUrl",UploadService.url(photo));
        } else {
            result.put("kind","TEXT");
            result.put("text",row.get("body"));
        }
        return result;
    }

    private UUID chatId(String id) { return Requests.id("chatId",id); }

    record Send(String clientMessageId,String kind,String text,String photoId) {}
    record Read(String lastReadMessageId) {}
}
