-- 로컬 거래의 예시 결제수단과 품목을 등록한다.
UPDATE categories SET allowed_tracks = CASE id WHEN 'softener' THEN ARRAY['SEALED_SALE'] ELSE ARRAY['RENTAL'] END
WHERE id IN ('keyboard','softener','cleaner');
INSERT INTO payment_methods (id,user_id,brand,last4) VALUES
    ('00000000-0000-0000-0000-000000000001','demo-borrower','DEMO','5678'),
    ('00000000-0000-0000-0000-000000000002','demo-owner','DEMO','5678')
ON CONFLICT DO NOTHING;
