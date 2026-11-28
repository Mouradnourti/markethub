-- Phase 8: shop discount codes. A code belongs to one shop and only discounts that shop's items.

CREATE TABLE coupons (
    id                BIGSERIAL PRIMARY KEY,
    shop_id           BIGINT         NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    code              VARCHAR(30)    NOT NULL,
    type              VARCHAR(10)    NOT NULL CHECK (type IN ('PERCENT', 'FIXED')),
    value             NUMERIC(12, 2) NOT NULL CHECK (value > 0),
    min_subtotal      NUMERIC(12, 2) CHECK (min_subtotal >= 0),
    starts_at         TIMESTAMPTZ,
    ends_at           TIMESTAMPTZ,
    max_uses          INT CHECK (max_uses > 0),
    used_count        INT            NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    once_per_customer BOOLEAN        NOT NULL DEFAULT TRUE,
    featured          BOOLEAN        NOT NULL DEFAULT FALSE,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_percent CHECK (type <> 'PERCENT' OR value <= 90),
    CONSTRAINT ck_coupons_window CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX ix_coupons_shop ON coupons (shop_id);

-- The code is copied onto the parcel so order history survives the coupon being deleted.
ALTER TABLE shop_orders
    ADD COLUMN coupon_id       BIGINT REFERENCES coupons (id) ON DELETE SET NULL,
    ADD COLUMN coupon_code     VARCHAR(30),
    ADD COLUMN discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0);
CREATE INDEX ix_shop_orders_coupon ON shop_orders (coupon_id);

ALTER TABLE orders ADD COLUMN discount_total NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (discount_total >= 0);
