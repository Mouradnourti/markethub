-- Phase 8: helpful votes, public seller replies and customer photos on reviews.

ALTER TABLE reviews
    ADD COLUMN helpful_count     INT         NOT NULL DEFAULT 0 CHECK (helpful_count >= 0),
    ADD COLUMN photo_urls        JSONB       NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN seller_reply      TEXT,
    ADD COLUMN seller_replied_at TIMESTAMPTZ;
CREATE INDEX ix_reviews_product_helpful ON reviews (product_id, helpful_count DESC, created_at DESC);

CREATE TABLE review_votes (
    review_id  BIGINT      NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (review_id, user_id)
);
CREATE INDEX ix_review_votes_user ON review_votes (user_id);
