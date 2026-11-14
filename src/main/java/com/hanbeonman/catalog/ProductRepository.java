// PostgreSQL에서 조회자 기준의 제품 범위와 정렬을 계산한다.
package com.hanbeonman.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProductRepository {
    // 두 동네 기준점의 거리를 미터로 계산한다.
    private static final String CANDIDATES = """
            WITH candidates AS (
                SELECT p.*, n.name AS neighborhood_name,
                       CASE WHEN e.category_id IS NULL THEN 0 ELSE 1 END AS experience_rank,
                       round(6371000 * 2 * asin(sqrt(least(1.0,
                           power(sin(radians(n.latitude - center.latitude) / 2), 2)
                           + cos(radians(center.latitude)) * cos(radians(n.latitude))
                           * power(sin(radians(n.longitude - center.longitude) / 2), 2)
                       ))))::integer AS distance_meters
                FROM products p
                JOIN neighborhoods n ON n.id = p.neighborhood_id
                JOIN neighborhoods center ON center.id = :neighborhoodId
                LEFT JOIN category_experiences e ON e.user_id = :userId AND e.category_id = p.category_id
            )
            """;
    private static final String LIST = CANDIDATES + """
            SELECT * FROM candidates
            WHERE distance_meters <= :radius
              AND NOT EXISTS (SELECT 1 FROM trades t WHERE t.product_id=candidates.id
                  AND (t.status NOT IN ('COMPLETED','REJECTED','CANCELED')
                       OR candidates.track='SEALED_SALE' AND t.status='COMPLETED'))
              AND (NOT :applyBudget OR CASE track
                  WHEN 'RENTAL' THEN deposit_amount + daily_fee_amount::numeric * :rentalDays
                  ELSE sale_price_amount
              END <= :budgetAmount)
              AND (experience_rank, distance_meters, id) > (:rank, :distance, :productId)
            ORDER BY experience_rank, distance_meters, id
            LIMIT :limit
            """;
    private static final String DETAIL = CANDIDATES + """
            SELECT * FROM candidates WHERE id = :productId AND distance_meters <= :radius
            """;

    private final JdbcClient jdbc;

    ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<String> findNeighborhood(String userId) {
        return jdbc.sql("""
                SELECT verified_neighborhood_id
                FROM app_users
                WHERE id = :userId AND verified_neighborhood_id IS NOT NULL
                """)
                .param("userId", userId)
                .query(String.class)
                .optional();
    }

    List<Product> list(ProductCursor.Conditions conditions, ProductCursor.Position position, int radius, int limit) {
        Long budget = conditions.budgetAmount();
        return scopedQuery(LIST, conditions.userId(), conditions.neighborhoodId(), radius)
                .param("applyBudget", budget != null)
                .param("budgetAmount", budget == null ? 0L : budget)
                .param("rentalDays", conditions.rentalDays())
                .param("rank", position.experienceRank())
                .param("distance", position.distanceMeters())
                .param("productId", position.productId())
                .param("limit", limit)
                .query((result, row) -> mapProduct(result))
                .list();
    }

    Optional<Product> find(String userId, String neighborhoodId, int radius, String productId) {
        return scopedQuery(DETAIL, userId, neighborhoodId, radius)
                .param("productId", productId)
                .query((result, row) -> mapProduct(result))
                .optional();
    }

    List<Product> mine(String userId, String neighborhoodId, String after, int limit) {
        return jdbc.sql(CANDIDATES + """
                SELECT * FROM candidates WHERE owner_id=:userId AND id>:after ORDER BY id LIMIT :limit
                """).param("userId",userId).param("neighborhoodId",neighborhoodId)
                .param("after",after).param("limit",limit).query((row,index) -> mapProduct(row)).list();
    }

    private JdbcClient.StatementSpec scopedQuery(String sql, String userId, String neighborhoodId, int radius) {
        return jdbc.sql(sql).param("userId", userId).param("neighborhoodId", neighborhoodId).param("radius", radius);
    }

    private Product mapProduct(ResultSet row) throws SQLException {
        return new Product(
                row.getString("id"), row.getString("track"), row.getString("title"), row.getString("category_id"),
                row.getString("thumbnail_url"), row.getString("neighborhood_name"), row.getInt("distance_meters"),
                row.getInt("experience_rank") == 0, row.getObject("deposit_amount", Long.class),
                row.getObject("daily_fee_amount", Long.class), row.getObject("sale_price_amount", Long.class),
                row.getString("description"), List.of((String[]) row.getArray("image_urls").getArray()),
                row.getObject("published_at", OffsetDateTime.class).toInstant()
        );
    }
}
