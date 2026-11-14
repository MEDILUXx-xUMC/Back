// 검증된 이미지 파일을 저장하고 권한에 맞게 제공한다.
package com.hanbeonman.upload;

import com.hanbeonman.common.http.Requests;
import com.hanbeonman.identity.CurrentUser;
import com.hanbeonman.trade.TradeAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
class UploadController {
    private static final Set<String> PURPOSES = Set.of("PRODUCT","HANDOFF","CONDITION","PICKUP","RETURN","DISPUTE","CHAT");
    private static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10};
    private final JdbcClient jdbc;
    private final CurrentUser users;
    private final TradeAccess trades;

    UploadController(JdbcClient jdbc, CurrentUser users, TradeAccess trades) {
        this.jdbc = jdbc;
        this.users = users;
        this.trades = trades;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> upload(HttpServletRequest request, @RequestParam("file") MultipartFile file,
            @RequestParam("purpose") String purpose, @RequestParam(name="tradeId",required=false) String tradeId) throws IOException {
        String user = users.require(request);
        if (!PURPOSES.contains(purpose) || "PRODUCT".equals(purpose) == (tradeId != null)) {
            throw com.hanbeonman.common.http.ApiException.invalidField("purpose","사진 용도와 거래를 확인해 주세요.");
        }
        UUID trade = tradeId == null ? null : Requests.id("tradeId",tradeId);
        if (trade != null) {
            trades.require(user,trade,false);
        }
        byte[] bytes = file.getBytes();
        if (bytes.length > 5 * 1024 * 1024) {
            throw Requests.error(HttpStatus.PAYLOAD_TOO_LARGE,"UPLOAD_TOO_LARGE","사진은 5MB 이하여야 해요.");
        }
        String type = imageType(bytes);
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO uploads(id,uploader_id,trade_id,purpose,content_type,content) VALUES (?,?,?,?,?,?)")
                .params(Arrays.asList(id,user,trade,purpose,type,bytes)).update();
        return Map.of("photoId",id);
    }

    @GetMapping("/{id}")
    ResponseEntity<byte[]> get(HttpServletRequest request,@PathVariable("id") String rawId) {
        UUID id = Requests.id("photoId",rawId);
        var photo = jdbc.sql("SELECT * FROM uploads WHERE id=?").param(id).query(new ColumnMapRowMapper()).optional()
                .orElseThrow(() -> Requests.error(HttpStatus.NOT_FOUND,"PHOTO_NOT_FOUND","사진을 찾을 수 없어요."));
        if (photo.get("product_id") == null) {
            String user = users.require(request);
            if (Boolean.TRUE.equals(photo.get("attached")) && photo.get("trade_id") instanceof UUID trade) {
                trades.require(user,trade,false);
            } else if (!user.equals(photo.get("uploader_id"))) {
                throw Requests.error(HttpStatus.NOT_FOUND,"PHOTO_NOT_FOUND","사진을 찾을 수 없어요.");
            }
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)photo.get("content_type")))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .body((byte[])photo.get("content"));
    }

    private String imageType(byte[] bytes) {
        if (bytes.length >= 8 && Arrays.equals(bytes,0,8,PNG,0,8)) return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) return "image/jpeg";
        throw com.hanbeonman.common.http.ApiException.invalidField("file","PNG 또는 JPEG 사진을 선택해 주세요.");
    }
}
