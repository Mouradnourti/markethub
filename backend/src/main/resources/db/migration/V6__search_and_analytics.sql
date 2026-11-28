-- Phase 8: typo-tolerant search (trigrams) and daily product page views for seller analytics.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_products_name_trgm ON products USING gin (lower(name) gin_trgm_ops);

CREATE TABLE product_views (
    product_id BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    day        DATE   NOT NULL,
    views      INT    NOT NULL DEFAULT 0 CHECK (views >= 0),
    PRIMARY KEY (product_id, day)
);
CREATE INDEX ix_product_views_day ON product_views (day);
