// 예산과 사용 일수를 검증한다.
package com.hanbeonman.catalog;

import com.hanbeonman.common.http.ApiException;

record ProductQuery(Long budgetAmount, Integer rentalDays, String cursor) {
    private static final long MAX_SAFE_AMOUNT = 9_007_199_254_740_991L;

    ProductQuery {
        if (budgetAmount != null && (budgetAmount < 0 || budgetAmount > MAX_SAFE_AMOUNT)) {
            throw ApiException.invalidField("budgetAmount", "예산은 0 이상의 안전한 정수여야 해요.");
        }
        if (rentalDays == null || rentalDays < 1) {
            throw ApiException.invalidField("rentalDays", "사용 기간은 1일 이상이어야 해요.");
        }
    }
}
