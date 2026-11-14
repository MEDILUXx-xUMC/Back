-- 로컬 제품 조회에 사용할 예시 데이터를 추가한다.
INSERT INTO neighborhoods (id, name, latitude, longitude) VALUES
    ('sau', '사우동', 37.6200, 126.7190),
    ('pungmu', '풍무동', 37.6155, 126.7190),
    ('gurae', '구래동', 37.6450, 126.6350)
ON CONFLICT DO NOTHING;

INSERT INTO app_users (id, display_name, verified_neighborhood_id) VALUES
    ('demo-borrower', '빌리는 이웃', 'sau'),
    ('demo-owner', '빌려주는 이웃', 'pungmu')
ON CONFLICT DO NOTHING;

INSERT INTO categories (id, name) VALUES
    ('keyboard', '기계식 키보드'),
    ('softener', '섬유유연제'),
    ('cleaner', '무선 청소기')
ON CONFLICT DO NOTHING;

INSERT INTO category_experiences (user_id, category_id) VALUES ('demo-borrower', 'softener')
ON CONFLICT DO NOTHING;

INSERT INTO products (id, owner_id, category_id, neighborhood_id, track, title, description,
                      deposit_amount, daily_fee_amount, sale_price_amount, published_at) VALUES
    ('product-keyboard', 'demo-owner', 'keyboard', 'pungmu', 'RENTAL',
     '기계식 키보드 (저소음 적축)', '조용한 타건감의 키보드입니다.', 45000, 2000, NULL, '2026-11-14T03:30:00Z'),
    ('product-softener', 'demo-owner', 'softener', 'sau', 'SEALED_SALE',
     '섬유유연제 코튼 블루 미니 (미개봉)', '뜯지 않은 여행용 제품입니다.', NULL, NULL, 1500, '2026-11-14T03:30:00Z'),
    ('product-cleaner', 'demo-owner', 'cleaner', 'gurae', 'RENTAL',
     '무선 청소기', '거래 반경 밖에 있는 예시 제품입니다.', 30000, 2000, NULL, '2026-11-14T03:30:00Z')
ON CONFLICT DO NOTHING;
