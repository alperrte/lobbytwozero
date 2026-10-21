-- AUTH-03/07/08/13/15: oturumlar, dönen refresh token'lar, deneme sınırı ve token sürümü.
-- Refresh token'ın kendisi saklanmaz; yalnız SHA-256 özeti (hex) tutulur.

ALTER TABLE accounts ADD COLUMN token_version bigint NOT NULL DEFAULT 0;

ALTER TABLE security_events ADD COLUMN request_id varchar(64);

-- Oturum = refresh token ailesi (JWT "sid"). İptal tek satırla ve atomik yapılır.
CREATE TABLE auth_sessions (
    id            uuid        PRIMARY KEY,
    account_id    uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    token_version bigint      NOT NULL,
    auth_methods  varchar(32) NOT NULL,
    created_at    timestamptz NOT NULL,
    expires_at    timestamptz NOT NULL,
    revoked_at    timestamptz,
    revoke_reason varchar(32),
    CONSTRAINT ck_auth_sessions_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_auth_sessions_reason CHECK (
        revoke_reason IS NULL OR revoke_reason IN ('LOGOUT', 'LOGOUT_ALL', 'REFRESH_REUSE', 'ACCOUNT_INACTIVE')
    ),
    CONSTRAINT ck_auth_sessions_revocation CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
);

CREATE INDEX ix_auth_sessions_account_active ON auth_sessions (account_id) WHERE revoked_at IS NULL;

CREATE TABLE refresh_tokens (
    id         uuid        PRIMARY KEY,
    session_id uuid        NOT NULL REFERENCES auth_sessions (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    rotated_at timestamptz,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_refresh_tokens_session_id ON refresh_tokens (session_id);

-- Hesap varlığından bağımsız sayaç: anahtar, kimlik bilgisinin HMAC özetidir (bilinmeyen hesaplar da sayılır).
CREATE TABLE attempt_throttles (
    key_hash          varchar(64) PRIMARY KEY,
    failed_attempts   integer     NOT NULL DEFAULT 0,
    window_started_at timestamptz NOT NULL,
    locked_until      timestamptz,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT ck_attempt_throttles_key_hash CHECK (key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_attempt_throttles_attempts CHECK (failed_attempts >= 0)
);
