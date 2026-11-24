-- Merchandising content for product pages: sale price, one-line pitch, key benefits and a spec sheet.
ALTER TABLE products
    ADD COLUMN compare_at_price NUMERIC(12, 2),
    ADD COLUMN tagline          VARCHAR(200),
    ADD COLUMN highlights       JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN specs            JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_products_compare_at_price CHECK (compare_at_price IS NULL OR compare_at_price > price);

-- "Deals" listings only scan discounted products.
CREATE INDEX ix_products_on_sale ON products (sold_count DESC) WHERE compare_at_price IS NOT NULL;
