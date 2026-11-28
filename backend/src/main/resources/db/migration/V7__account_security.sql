-- Phase 8: brute-force lockout and optional two-factor sign-in (TOTP, RFC 6238).

ALTER TABLE users
    ADD COLUMN failed_login_count INT     NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    ADD COLUMN locked_until       TIMESTAMPTZ,
    -- AES-GCM encrypted base32 secret; never stored in clear.
    ADD COLUMN totp_secret        VARCHAR(255),
    ADD COLUMN totp_enabled       BOOLEAN NOT NULL DEFAULT FALSE;
