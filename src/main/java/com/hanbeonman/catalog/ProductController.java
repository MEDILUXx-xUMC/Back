// 제품 목록과 상세 조회의 HTTP 진입점을 제공한다.
package com.hanbeonman.catalog;

import com.hanbeonman.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final CurrentUser users;

    ProductController(ProductService products, Environment environment, CurrentUser users) {
        this.products = products;
        this.local = environment.acceptsProfiles(Profiles.of("local"));
        this.users = users;
    }

    @GetMapping
    ProductResponse.Page list(
            @RequestParam(name = "budgetAmount", required = false) Long budgetAmount,
            @RequestParam(name = "rentalDays", required = false) Integer rentalDays,
            @RequestParam(name = "cursor", required = false) String cursor,
            HttpServletRequest request
    ) {
        String userId = userId(request);
        return products.list(userId, new ProductQuery(budgetAmount, rentalDays, cursor));
    }

    @GetMapping("/{productId}")
    Map<String, Object> get(@PathVariable("productId") String productId, HttpServletRequest request) {
        return products.get(userId(request), productId);
    }

    private String userId(HttpServletRequest request) {
        if (local && request.getHeader("Authorization") == null && request.getUserPrincipal() == null) {
            return "demo-borrower";
        }
        return users.require(request);
    }
}
