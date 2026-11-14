// FE 계약에 맞춰 제품 목록·상세 본문을 만든다.
package com.hanbeonman.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProductResponse {
    private ProductResponse() {}

    static Map<String, Object> summary(Product product) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("productId", product.productId());
        fields.put("track", product.track());
        fields.put("title", product.title());
        fields.put("categoryId", product.categoryId());
        fields.put("thumbnailUrl", product.thumbnailUrl());
        fields.put("neighborhoodName", product.neighborhoodName());
        fields.put("distanceMeters", product.distanceMeters());
        fields.put("isFirstExperience", product.firstExperience());
        if ("RENTAL".equals(product.track())) {
            fields.put("depositAmount", product.depositAmount());
            fields.put("dailyFeeAmount", product.dailyFeeAmount());
        } else {
            fields.put("salePriceAmount", product.salePriceAmount());
        }
        return fields;
    }

    static Map<String, Object> detail(Product product) {
        Map<String, Object> fields = summary(product);
        fields.put("description", product.description());
        fields.put("imageUrls", product.imageUrls());
        fields.put("publishedAt", product.publishedAt());
        return fields;
    }

    record Page(List<Map<String, Object>> items, String nextCursor) {}
}
