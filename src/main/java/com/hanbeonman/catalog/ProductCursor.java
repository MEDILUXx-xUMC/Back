// 현재 조회 조건과 마지막 정렬 위치를 커서로 표현한다.
package com.hanbeonman.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanbeonman.common.http.ApiException;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class ProductCursor {
    private final ObjectMapper json;

    ProductCursor(ObjectMapper json) {
        this.json = json;
    }

    String encode(Conditions conditions, Product product) {
        var position = new Position(product.firstExperience() ? 0 : 1, product.distanceMeters(), product.productId());
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.writeValueAsBytes(new Token(conditions, position)));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not encode product cursor", error);
        }
    }

    Position decode(String cursor, Conditions conditions) {
        if (cursor == null) {
            return new Position(-1, 0, "");
        }
        if (cursor.length() > 2048) {
            throw invalidCursor();
        }
        try {
            Token token = json.readValue(Base64.getUrlDecoder().decode(cursor), Token.class);
            Position position = token == null ? null : token.position();
            if (position == null || !Objects.equals(token.conditions(), conditions)
                    || position.experienceRank() == null || position.experienceRank() < 0 || position.experienceRank() > 1
                    || position.distanceMeters() == null || position.distanceMeters() < 0
                    || position.productId() == null || position.productId().isBlank() || position.productId().length() > 64) {
                throw invalidCursor();
            }
            return position;
        } catch (IllegalArgumentException | IOException error) {
            throw invalidCursor();
        }
    }

    private ApiException invalidCursor() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "목록을 처음부터 다시 불러와 주세요.");
    }

    record Conditions(String userId, String neighborhoodId, Long budgetAmount, int rentalDays) {}
    record Position(Integer experienceRank, Integer distanceMeters, String productId) {}
    private record Token(Conditions conditions, Position position) {}
}
