// 제품 목록과 상세 조회의 HTTP 진입점을 제공한다.
package com.hanbeonman.catalog;

import com.hanbeonman.common.http.ApiException;
import java.security.Principal;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
class ProductController {
    private final ProductService products;
    private final boolean local;

    ProductController(ProductService products, Environment environment) {
        this.products = products;
        this.local = environment.acceptsProfiles(Profiles.of("local"));
    }

    @GetMapping
    ProductResponse.Page list(
            @RequestParam(name = "budgetAmount", required = false) Long budgetAmount,
            @RequestParam(name = "rentalDays", required = false) Integer rentalDays,
            @RequestParam(name = "cursor", required = false) String cursor,
            Principal principal
    ) {
        String userId = userId(principal);
        return products.list(userId, new ProductQuery(budgetAmount, rentalDays, cursor));
    }

    @GetMapping("/{productId}")
    Map<String, Object> get(@PathVariable("productId") String productId, Principal principal) {
        return products.get(userId(principal), productId);
    }

    private String userId(Principal principal) {
        if (principal != null) {
            return principal.getName();
        }
        if (local) {
            return "demo-borrower";
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인해 주세요.");
    }
}
