-- Phase 8: wishlist with stock and price alerts, in-app notifications, abandoned-cart reminders.

CREATE TABLE wishlist_items (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id       BIGINT         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    -- Price when saved, and the last price we alerted about, so a price drop is announced only once.
    price_when_added NUMERIC(12, 2) NOT NULL,
    last_alert_price NUMERIC(12, 2),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_wishlist_user_product UNIQUE (user_id, product_id)
);
CREATE INDEX ix_wishlist_product ON wishlist_items (product_id);

CREATE TABLE user_notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(160) NOT NULL,
    body       VARCHAR(500),
    link       VARCHAR(255),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_user_notifications_user ON user_notifications (user_id, created_at DESC);
CREATE INDEX ix_user_notifications_unread ON user_notifications (user_id) WHERE read_at IS NULL;

ALTER TABLE users ADD COLUMN cart_reminder_sent_at TIMESTAMPTZ;
