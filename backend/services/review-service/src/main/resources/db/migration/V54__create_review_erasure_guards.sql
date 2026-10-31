CREATE TABLE review_erasure_guards (
    account_hash varchar(64) PRIMARY KEY,
    expires_at timestamptz NOT NULL,
    CONSTRAINT ck_review_erasure_guards_hash CHECK (account_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_review_erasure_guards_expiry ON review_erasure_guards (expires_at);
