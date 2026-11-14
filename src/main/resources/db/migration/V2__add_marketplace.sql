-- 온보딩, 거래, 사진과 채팅 데이터를 저장한다.
CREATE TABLE user_sessions (token_hash text PRIMARY KEY, user_id varchar(64) NOT NULL REFERENCES app_users(id), expires_at timestamptz NOT NULL);
CREATE TABLE policies (id text NOT NULL, version text NOT NULL, title text NOT NULL, content text NOT NULL, required boolean NOT NULL, PRIMARY KEY(id,version));
CREATE TABLE user_consents (user_id varchar(64) REFERENCES app_users(id), policy_id text, version text, agreed_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,policy_id,version), FOREIGN KEY(policy_id,version) REFERENCES policies(id,version));
CREATE TABLE payment_methods (id uuid PRIMARY KEY, user_id varchar(64) NOT NULL REFERENCES app_users(id), brand text NOT NULL, last4 char(4) NOT NULL);
ALTER TABLE category_experiences ADD COLUMN first_experienced_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE categories ADD COLUMN allowed_tracks text[] NOT NULL DEFAULT '{}';
UPDATE categories SET allowed_tracks = CASE id WHEN 'softener' THEN ARRAY['SEALED_SALE'] ELSE ARRAY['RENTAL'] END;

CREATE TABLE trade_quotes (
    id uuid PRIMARY KEY, product_id varchar(64) NOT NULL REFERENCES products(id),
    requester_id varchar(64) NOT NULL REFERENCES app_users(id), rental_days integer,
    deposit_amount bigint, charge_amount bigint NOT NULL, expires_at timestamptz NOT NULL
);
CREATE TABLE trades (
    id uuid PRIMARY KEY, quote_id uuid NOT NULL UNIQUE REFERENCES trade_quotes(id),
    requester_id varchar(64) NOT NULL REFERENCES app_users(id), owner_id varchar(64) NOT NULL REFERENCES app_users(id),
    product_id varchar(64) NOT NULL REFERENCES products(id), payment_method_id uuid NOT NULL REFERENCES payment_methods(id),
    idempotency_key text NOT NULL, status text NOT NULL, payment_status text NOT NULL,
    address text, instructions text, pickup_deadline_at timestamptz, return_due_at timestamptz,
    returned_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(requester_id,idempotency_key), CHECK (requester_id <> owner_id)
);
CREATE UNIQUE INDEX trade_active_product ON trades(product_id)
    WHERE status NOT IN ('COMPLETED','REJECTED','CANCELED');
CREATE TABLE uploads (
    id uuid PRIMARY KEY, uploader_id varchar(64) NOT NULL REFERENCES app_users(id), trade_id uuid REFERENCES trades(id),
    product_id varchar(64) REFERENCES products(id), purpose text NOT NULL, content_type text NOT NULL,
    content bytea NOT NULL, attached boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE trade_disputes (id uuid PRIMARY KEY, trade_id uuid NOT NULL UNIQUE REFERENCES trades(id), reporter_id varchar(64) NOT NULL REFERENCES app_users(id), reason text NOT NULL);
CREATE TABLE trade_reviews (id uuid PRIMARY KEY, trade_id uuid NOT NULL UNIQUE REFERENCES trades(id), body text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE chat_messages (
    id uuid PRIMARY KEY, position bigint GENERATED ALWAYS AS IDENTITY UNIQUE, trade_id uuid NOT NULL REFERENCES trades(id),
    sender_id varchar(64) NOT NULL REFERENCES app_users(id), client_message_id text NOT NULL,
    body text, photo_id uuid REFERENCES uploads(id), created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(trade_id,sender_id,client_message_id), CHECK ((body IS NULL) <> (photo_id IS NULL))
);
CREATE TABLE chat_reads (trade_id uuid REFERENCES trades(id), user_id varchar(64) REFERENCES app_users(id), position bigint NOT NULL, PRIMARY KEY(trade_id,user_id));
CREATE INDEX chat_message_position ON chat_messages(trade_id,position DESC);
