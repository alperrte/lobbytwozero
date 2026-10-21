-- AUTH-09/10: parola sıfırlama token'ları. Token'ın kendisi saklanmaz; yalnız SHA-256 özeti (hex) tutulur.

CREATE TABLE password_reset_tokens (
    id                  uuid        PRIMARY KEY,
    account_id          uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    token_hash          varchar(64) NOT NULL,
    created_at          timestamptz NOT NULL,
    expires_at          timestamptz NOT NULL,
    consumed_at         timestamptz,
    invalidated_at      timestamptz,
    invalidation_reason varchar(32),
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_password_reset_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_password_reset_tokens_reason CHECK (
        invalidation_reason IS NULL OR invalidation_reason IN ('SUPERSEDED', 'DELIVERY_FAILED', 'PASSWORD_CHANGED')
    ),
    CONSTRAINT ck_password_reset_tokens_invalidation CHECK ((invalidated_at IS NULL) = (invalidation_reason IS NULL)),
    CONSTRAINT ck_password_reset_tokens_single_outcome CHECK (consumed_at IS NULL OR invalidated_at IS NULL)
);

-- Hesap başına en fazla bir aktif sıfırlama token'ı.
CREATE UNIQUE INDEX uq_password_reset_tokens_active
    ON password_reset_tokens (account_id)
    WHERE consumed_at IS NULL AND invalidated_at IS NULL;

CREATE INDEX ix_password_reset_tokens_issued ON password_reset_tokens (account_id, created_at);

-- Parola değişikliği/sıfırlama oturumları bu nedenlerle iptal eder.
ALTER TABLE auth_sessions DROP CONSTRAINT ck_auth_sessions_reason;
ALTER TABLE auth_sessions ADD CONSTRAINT ck_auth_sessions_reason CHECK (
    revoke_reason IS NULL OR revoke_reason IN (
        'LOGOUT', 'LOGOUT_ALL', 'REFRESH_REUSE', 'ACCOUNT_INACTIVE', 'PASSWORD_CHANGED', 'PASSWORD_RESET'
    )
);
