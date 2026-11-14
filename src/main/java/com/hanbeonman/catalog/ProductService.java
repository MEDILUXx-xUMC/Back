// 동네 확인과 페이지 처리를 제품 조회에 적용한다.
package com.hanbeonman.catalog;

import com.hanbeonman.common.http.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class ProductService {
    private static final int RADIUS_METERS = 1000;
    private static final int PAGE_SIZE = 20;

    private final ProductRepository products;
    private final ProductCursor cursors;

    ProductService(ProductRepository products, ProductCursor cursors) {
        this.products = products;
        this.cursors = cursors;
    }

    ProductResponse.Page list(String userId, ProductQuery query) {
        var conditions = new ProductCursor.Conditions(
                userId, neighborhood(userId), query.budgetAmount(), query.rentalDays());
        var position = cursors.decode(query.cursor(), conditions);
        var results = products.list(conditions, position, RADIUS_METERS, PAGE_SIZE + 1);
        var page = results.stream().limit(PAGE_SIZE).map(ProductResponse::summary).toList();
        String next = results.size() > PAGE_SIZE ? cursors.encode(conditions, results.get(PAGE_SIZE - 1)) : null;
        return new ProductResponse.Page(page, next);
    }

    Map<String, Object> get(String userId, String productId) {
        return products.find(userId, neighborhood(userId), RADIUS_METERS, productId)
                .map(ProductResponse::detail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "제품을 찾을 수 없어요."));
    }

    ProductResponse.Page mine(String userId, String cursor) {
        String neighborhood = neighborhood(userId);
        var conditions = new ProductCursor.Conditions(userId, neighborhood, null, 0);
        var position = cursors.decode(cursor, conditions);
        var results = products.mine(userId, neighborhood, position.productId(), PAGE_SIZE + 1);
        var items = results.stream().limit(PAGE_SIZE).map(ProductResponse::detail).toList();
        String next = results.size() > PAGE_SIZE ? cursors.encode(conditions, results.get(PAGE_SIZE - 1)) : null;
        return new ProductResponse.Page(items, next);
    }

    String neighborhood(String userId) {
        return products.findNeighborhood(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "NEIGHBORHOOD_VERIFICATION_REQUIRED", "동네를 인증해 주세요."));
    }
}
