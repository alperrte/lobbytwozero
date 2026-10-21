-- AUTH-04: TOTP (RFC 6238). Secret AES-256-GCM ile şifreli; kurtarma kodları ve MFA challenge'ları özet olarak tutulur.

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

CREATE TABLE mfa_challenges (
    id             uuid        PRIMARY KEY,
    account_id     uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    challenge_hash varchar(64) NOT NULL,
    attempts       integer     NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL,
    expires_at     timestamptz NOT NULL,
    consumed_at    timestamptz,
    CONSTRAINT uq_mfa_challenges_challenge_hash UNIQUE (challenge_hash),
    CONSTRAINT ck_mfa_challenges_challenge_hash CHECK (challenge_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_mfa_challenges_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_mfa_challenges_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_mfa_challenges_account_id ON mfa_challenges (account_id);
