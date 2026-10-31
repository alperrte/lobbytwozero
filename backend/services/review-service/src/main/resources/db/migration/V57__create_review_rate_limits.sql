CREATE TABLE review_rate_limits (
    account_hash varchar(64) NOT NULL,
    action varchar(32) NOT NULL,
    window_start timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts integer NOT NULL,
    PRIMARY KEY (account_hash, action, window_start),
    CONSTRAINT ck_review_rate_limits_hash CHECK (account_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_review_rate_limits_action CHECK (action ~ '^[A-Z][A-Z_]{0,31}$'),
    CONSTRAINT ck_review_rate_limits_attempts CHECK (attempts >= 1),
    CONSTRAINT ck_review_rate_limits_window CHECK (expires_at > window_start)
);
CREATE INDEX ix_review_rate_limits_expiry ON review_rate_limits (expires_at);
