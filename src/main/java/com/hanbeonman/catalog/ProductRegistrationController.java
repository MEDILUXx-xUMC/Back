// 제품 등록과 소유자 목록을 제공한다.
package com.hanbeonman.catalog;

import com.hanbeonman.common.http.ApiException;
import com.hanbeonman.common.http.Requests;
import com.hanbeonman.identity.CurrentUser;
import com.hanbeonman.upload.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class ProductRegistrationController {
    private final JdbcClient jdbc;
    private final CurrentUser users;
    private final ProductService products;
    private final UploadService uploads;

    ProductRegistrationController(JdbcClient jdbc, CurrentUser users, ProductService products, UploadService uploads) {
        this.jdbc = jdbc;
        this.users = users;
        this.products = products;
        this.uploads = uploads;
    }

    @GetMapping("/categories")
    Map<String,Object> categories(HttpServletRequest request) {
        users.require(request);
        return Map.of("items",jdbc.sql("SELECT id,name,allowed_tracks FROM categories ORDER BY name,id")
                .query((row,index) -> Map.of("categoryId",row.getString("id"),"name",row.getString("name"),
                        "allowedTracks",row.getArray("allowed_tracks").getArray())).list());
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String,Object> create(HttpServletRequest request,@RequestBody Listing body) {
        String user = users.require(request);
        String neighborhood = products.neighborhood(user);
        String track = Requests.text("track",body.track(),16);
        String title = Requests.text("title",body.title(),100);
        String description = Requests.text("description",body.description(),2000);
        String category = Requests.text("categoryId",body.categoryId(),64);
        boolean permitted = jdbc.sql("SELECT EXISTS(SELECT 1 FROM categories WHERE id=? AND ?=ANY(allowed_tracks))")
                .params(category,track).query(Boolean.class).single();
        boolean rental = "RENTAL".equals(track);
        boolean eligible = rental ? Boolean.TRUE.equals(body.kcMarked()) && Boolean.FALSE.equals(body.modified())
                : "SEALED_SALE".equals(track) && Boolean.TRUE.equals(body.sealed()) && Boolean.TRUE.equals(body.retailOriginal());
        if (!permitted || !eligible) {
            throw Requests.error(HttpStatus.UNPROCESSABLE_ENTITY,"PRODUCT_NOT_ELIGIBLE","등록 가능한 품목인지 확인해 주세요.");
        }
        Long deposit = rental ? Requests.amount("depositAmount",body.depositAmount()) : null;
        Long fee = rental ? Requests.amount("dailyFeeAmount",body.dailyFeeAmount()) : null;
        Long sale = rental ? null : Requests.amount("salePriceAmount",body.salePriceAmount());
        if (body.imageIds() == null || body.imageIds().isEmpty() || body.imageIds().size() > 5) {
            throw ApiException.invalidField("imageIds","제품 사진은 1장부터 5장까지 등록할 수 있어요.");
        }
        String id = UUID.randomUUID().toString();
        jdbc.sql("""
                INSERT INTO products(id,owner_id,category_id,neighborhood_id,track,title,description,
                    deposit_amount,daily_fee_amount,sale_price_amount,published_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,now())
                """).params(Arrays.asList(id,user,category,neighborhood,track,title,description,deposit,fee,sale)).update();
        List<String> urls = body.imageIds().stream().map(photo -> uploads.attach(photo,user,null,id,"PRODUCT")).toList();
        // 업로드 수와 소유권을 검증한 뒤 배열을 저장한다.
        jdbc.sql("UPDATE products SET thumbnail_url=?, image_urls=string_to_array(?, ',') WHERE id=?")
                .params(urls.getFirst(),String.join(",",urls),id).update();
        return products.get(user,id);
    }

    @GetMapping("/me/products")
    ProductResponse.Page mine(HttpServletRequest request,@RequestParam(name="cursor",required=false) String cursor) {
        return products.mine(users.require(request),cursor);
    }

    record Listing(String track,String title,String description,String categoryId,List<String> imageIds,
                   Long depositAmount,Long dailyFeeAmount,Long salePriceAmount,Boolean kcMarked,
                   Boolean modified,Boolean sealed,Boolean retailOriginal) {}
}
