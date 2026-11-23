-- MarketHub initial schema. See docs/erd.md.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    first_name    VARCHAR(80)  NOT NULL,
    last_name     VARCHAR(80)  NOT NULL,
    phone         VARCHAR(30),
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'SELLER', 'ADMIN')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email)
);
CREATE INDEX ix_users_role ON users (role);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE shops (
    id              BIGSERIAL PRIMARY KEY,
    owner_id        BIGINT       NOT NULL REFERENCES users (id),
    name            VARCHAR(120) NOT NULL,
    slug            VARCHAR(140) NOT NULL,
    description     TEXT,
    logo_url        VARCHAR(500),
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    commission_rate NUMERIC(5, 2) CHECK (commission_rate BETWEEN 0 AND 100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_shops_owner UNIQUE (owner_id),
    CONSTRAINT uk_shops_slug UNIQUE (slug)
);
CREATE INDEX ix_shops_status ON shops (status);

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT REFERENCES categories (id) ON DELETE SET NULL,
    name        VARCHAR(80)  NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
);
CREATE INDEX ix_categories_parent ON categories (parent_id);

CREATE TABLE products (
    id           BIGSERIAL PRIMARY KEY,
    shop_id      BIGINT         NOT NULL REFERENCES shops (id),
    category_id  BIGINT         NOT NULL REFERENCES categories (id),
    name         VARCHAR(200)   NOT NULL,
    description  TEXT,
    price        NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    stock        INT            NOT NULL DEFAULT 0 CHECK (stock >= 0),
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    avg_rating   NUMERIC(3, 2)  NOT NULL DEFAULT 0,
    review_count INT            NOT NULL DEFAULT 0,
    sold_count   INT            NOT NULL DEFAULT 0,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_shop ON products (shop_id);
CREATE INDEX ix_products_active_created ON products (active, created_at DESC);
CREATE INDEX ix_products_price ON products (price);
CREATE INDEX ix_products_name_lower ON products (lower(name));

CREATE TABLE product_images (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    position   INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_product_images_product ON product_images (product_id);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    quantity   INT         NOT NULL CHECK (quantity > 0),
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_cart_items_user_product UNIQUE (user_id, product_id)
);

CREATE TABLE orders (
    id                    BIGSERIAL PRIMARY KEY,
    reference             VARCHAR(30)    NOT NULL,
    customer_id           BIGINT         NOT NULL REFERENCES users (id),
    payment_method        VARCHAR(10)    NOT NULL CHECK (payment_method IN ('COD', 'CARD')),
    payment_status        VARCHAR(20)    NOT NULL CHECK (payment_status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    subtotal              NUMERIC(12, 2) NOT NULL,
    shipping_fee          NUMERIC(12, 2) NOT NULL,
    total                 NUMERIC(12, 2) NOT NULL,
    shipping_full_name    VARCHAR(160)   NOT NULL,
    shipping_phone        VARCHAR(30)    NOT NULL,
    shipping_address_line VARCHAR(255)   NOT NULL,
    shipping_city         VARCHAR(100)   NOT NULL,
    shipping_postal_code  VARCHAR(20)    NOT NULL,
    shipping_country      VARCHAR(80)    NOT NULL,
    return_risk_score     NUMERIC(5, 4),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_orders_reference UNIQUE (reference)
);
CREATE INDEX ix_orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX ix_orders_created ON orders (created_at);

CREATE TABLE shop_orders (
    id                BIGSERIAL PRIMARY KEY,
    order_id          BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    shop_id           BIGINT         NOT NULL REFERENCES shops (id),
    status            VARCHAR(20)    NOT NULL CHECK (status IN
        ('AWAITING_PAYMENT', 'PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'RETURNED')),
    subtotal          NUMERIC(12, 2) NOT NULL,
    commission_rate   NUMERIC(5, 2)  NOT NULL,
    commission_amount NUMERIC(12, 2) NOT NULL,
    tracking_number   VARCHAR(60),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_shop_orders_shop_status ON shop_orders (shop_id, status);
CREATE INDEX ix_shop_orders_order ON shop_orders (order_id);

CREATE TABLE order_items (
    id            BIGSERIAL PRIMARY KEY,
    shop_order_id BIGINT         NOT NULL REFERENCES shop_orders (id) ON DELETE CASCADE,
    product_id    BIGINT         NOT NULL REFERENCES products (id),
    product_name  VARCHAR(200)   NOT NULL,
    unit_price    NUMERIC(12, 2) NOT NULL,
    quantity      INT            NOT NULL CHECK (quantity > 0),
    line_total    NUMERIC(12, 2) NOT NULL
);
CREATE INDEX ix_order_items_shop_order ON order_items (shop_order_id);
CREATE INDEX ix_order_items_product ON order_items (product_id);

CREATE TABLE order_status_history (
    id            BIGSERIAL PRIMARY KEY,
    shop_order_id BIGINT      NOT NULL REFERENCES shop_orders (id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL,
    note          VARCHAR(255),
    changed_by    BIGINT REFERENCES users (id),
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_status_history_shop_order ON order_status_history (shop_order_id);

CREATE TABLE payments (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    provider           VARCHAR(20)    NOT NULL CHECK (provider IN ('STRIPE', 'STRIPE_MOCK', 'COD')),
    provider_reference VARCHAR(255),
    amount             NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    status             VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_payments_order ON payments (order_id);
CREATE INDEX ix_payments_provider_ref ON payments (provider_reference);

CREATE TABLE reviews (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating     INT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_reviews_product_user UNIQUE (product_id, user_id)
);

CREATE TABLE disputes (
    id            BIGSERIAL PRIMARY KEY,
    shop_order_id BIGINT       NOT NULL REFERENCES shop_orders (id) ON DELETE CASCADE,
    customer_id   BIGINT       NOT NULL REFERENCES users (id),
    type          VARCHAR(20)  NOT NULL CHECK (type IN ('RETURN', 'DISPUTE')),
    reason        VARCHAR(500) NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'APPROVED', 'REJECTED')),
    admin_note    VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ
);
CREATE INDEX ix_disputes_status ON disputes (status);
CREATE INDEX ix_disputes_shop_order ON disputes (shop_order_id);

CREATE TABLE platform_settings (
    key   VARCHAR(80)  PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);

INSERT INTO platform_settings (key, value) VALUES
    ('commission.default_rate', '10.00'),
    ('shipping.flat_fee', '4.99'),
    ('shipping.free_threshold', '50.00');
