// 제품 조회 결과와 트랙별 금액을 담는다.
package com.hanbeonman.catalog;

import java.time.Instant;
import java.util.List;

record Product(
        String productId, String track, String title, String categoryId,
        String thumbnailUrl, String neighborhoodName, int distanceMeters,
        boolean firstExperience, Long depositAmount, Long dailyFeeAmount, Long salePriceAmount,
        String description, List<String> imageUrls, Instant publishedAt
) {}
