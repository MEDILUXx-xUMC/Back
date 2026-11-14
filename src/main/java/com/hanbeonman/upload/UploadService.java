// 사진의 소유자, 용도와 연결 상태를 검사한다.
package com.hanbeonman.upload;

import com.hanbeonman.common.http.Requests;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class UploadService {
    private final JdbcClient jdbc;

    UploadService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String attach(String photo, String user, UUID trade, String product, String purpose) {
        UUID id = Requests.id("photoId", photo);
        int linked = jdbc.sql("""
                UPDATE uploads SET attached=true, product_id=? WHERE id=? AND uploader_id=?
                  AND trade_id IS NOT DISTINCT FROM CAST(? AS uuid) AND purpose=? AND NOT attached
                """).params(java.util.Arrays.asList(product,id,user,trade,purpose)).update();
        if (linked != 1) {
            throw Requests.error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PHOTO","선택한 사진의 용도를 확인해 주세요.");
        }
        return url(id);
    }

    public static String url(UUID id) {
        return "/api/v1/uploads/" + id;
    }
}
