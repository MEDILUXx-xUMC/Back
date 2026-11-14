-- 제품 조회에 필요한 사용자, 동네, 카테고리와 상품을 정의한다.
CREATE TABLE neighborhoods (
    id varchar(64) PRIMARY KEY,
    name text NOT NULL,
    latitude double precision NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude double precision NOT NULL CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE app_users (
    id varchar(64) PRIMARY KEY,
    display_name text NOT NULL,
    verified_neighborhood_id varchar(64) REFERENCES neighborhoods (id)
);

CREATE TABLE categories (
    id varchar(64) PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE category_experiences (
    user_id varchar(64) REFERENCES app_users (id),
    category_id varchar(64) REFERENCES categories (id),
    PRIMARY KEY (user_id, category_id)
);

CREATE TABLE products (
    id varchar(64) PRIMARY KEY,
    owner_id varchar(64) NOT NULL REFERENCES app_users (id),
    category_id varchar(64) NOT NULL REFERENCES categories (id),
    neighborhood_id varchar(64) NOT NULL REFERENCES neighborhoods (id),
    track varchar(16) NOT NULL,
    title text NOT NULL,
    description text NOT NULL,
    thumbnail_url text,
    image_urls text[] NOT NULL DEFAULT '{}',
    deposit_amount bigint CHECK (deposit_amount BETWEEN 0 AND 9007199254740991),
    daily_fee_amount bigint CHECK (daily_fee_amount BETWEEN 0 AND 9007199254740991),
    sale_price_amount bigint CHECK (sale_price_amount BETWEEN 0 AND 9007199254740991),
    published_at timestamptz NOT NULL,
    CHECK (
        (track = 'RENTAL' AND deposit_amount IS NOT NULL AND daily_fee_amount IS NOT NULL AND sale_price_amount IS NULL)
        OR (track = 'SEALED_SALE' AND sale_price_amount IS NOT NULL AND deposit_amount IS NULL AND daily_fee_amount IS NULL)
    )
);
