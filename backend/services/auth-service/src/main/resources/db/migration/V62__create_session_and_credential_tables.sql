-- AUTH-02/03/04/07/08/09/10/13/15: oturumlar, refresh token'lar, deneme sınırı, doğrulama kodları, parola sıfırlama
-- ve TOTP. Gizli değerlerin kendisi saklanmaz; yalnız özetleri (HMAC-SHA256 / SHA-256, hex) tutulur.

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
        revoke_reason IS NULL OR revoke_reason IN (
            'LOGOUT', 'LOGOUT_ALL', 'REFRESH_REUSE', 'ACCOUNT_INACTIVE', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
            'ROLE_CHANGED', 'ACCOUNT_DELETION'
        )
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

-- 6 haneli e-posta doğrulama kodları.
CREATE TABLE email_verification_codes (
    id                  uuid        PRIMARY KEY,
    account_id          uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    purpose             varchar(32) NOT NULL,
    code_hash           varchar(64) NOT NULL,
    attempts            integer     NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL,
    expires_at          timestamptz NOT NULL,
    consumed_at         timestamptz,
    invalidated_at      timestamptz,
    invalidation_reason varchar(32),
    CONSTRAINT ck_email_verification_codes_purpose CHECK (purpose IN ('EMAIL_VERIFICATION')),
    CONSTRAINT ck_email_verification_codes_code_hash CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_email_verification_codes_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_email_verification_codes_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_email_verification_codes_reason CHECK (
        invalidation_reason IS NULL OR invalidation_reason IN ('SUPERSEDED', 'DELIVERY_FAILED')
    ),
    CONSTRAINT ck_email_verification_codes_invalidation CHECK (
        (invalidated_at IS NULL) = (invalidation_reason IS NULL)
    ),
    CONSTRAINT ck_email_verification_codes_single_outcome CHECK (
        consumed_at IS NULL OR invalidated_at IS NULL
    )
);

-- Hesap ve amaç başına en fazla bir aktif kod.
CREATE UNIQUE INDEX uq_email_verification_codes_active
    ON email_verification_codes (account_id, purpose)
    WHERE consumed_at IS NULL AND invalidated_at IS NULL;

-- Yeniden gönderme beklemesi ve günlük sınır sorguları.
CREATE INDEX ix_email_verification_codes_issued
    ON email_verification_codes (account_id, purpose, created_at);

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

-- TOTP (RFC 6238): sır AES-256-GCM ile şifreli; kurtarma kodları ve MFA challenge'ları özet olarak tutulur.
CREATE TABLE totp_credentials (
    account_id        uuid        PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    secret_ciphertext bytea       NOT NULL,
    secret_nonce      bytea       NOT NULL,
    key_version       smallint    NOT NULL,
    status            varchar(16) NOT NULL,
    last_used_step    bigint,
    created_at        timestamptz NOT NULL,
    activated_at      timestamptz,
    version           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_totp_credentials_status CHECK (status IN ('PENDING', 'ACTIVE')),
    CONSTRAINT ck_totp_credentials_nonce CHECK (octet_length(secret_nonce) = 12),
    CONSTRAINT ck_totp_credentials_activation CHECK ((status = 'ACTIVE') = (activated_at IS NOT NULL))
);

CREATE TABLE recovery_codes (
    id         uuid        PRIMARY KEY,
    account_id uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    code_hash  varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    used_at    timestamptz,
    CONSTRAINT uq_recovery_codes_account_hash UNIQUE (account_id, code_hash),
    CONSTRAINT ck_recovery_codes_code_hash CHECK (code_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_recovery_codes_account_unused ON recovery_codes (account_id) WHERE used_at IS NULL;

-- first_factor: parola dışı ilk faktör (harici giriş) 2FA challenge'ında amr değerini doğru taşır.
CREATE TABLE mfa_challenges (
    id             uuid        PRIMARY KEY,
    account_id     uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    challenge_hash varchar(64) NOT NULL,
    first_factor   varchar(16) NOT NULL DEFAULT 'PWD',
    attempts       integer     NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL,
    expires_at     timestamptz NOT NULL,
    consumed_at    timestamptz,
    CONSTRAINT uq_mfa_challenges_challenge_hash UNIQUE (challenge_hash),
    CONSTRAINT ck_mfa_challenges_challenge_hash CHECK (challenge_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_mfa_challenges_first_factor CHECK (first_factor IN ('PWD', 'EXTERNAL')),
    CONSTRAINT ck_mfa_challenges_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_mfa_challenges_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_mfa_challenges_account_id ON mfa_challenges (account_id);
