-- AUTH-17: masaüstü cihaz kimliği (B yolu). Cihazın gizli anahtarı saklanmaz; yalnız SHA-256 özeti (hex) tutulur.
-- Cihaz token'ı (dev+jwt) bu kayıttan türetilir; kaydın iptali yeni token alınmasını anında durdurur.

CREATE TABLE device_credentials (
    id            uuid        PRIMARY KEY,
    account_id    uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    label         varchar(64) NOT NULL,
    secret_hash   varchar(64) NOT NULL,
    status        varchar(16) NOT NULL,
    created_at    timestamptz NOT NULL,
    expires_at    timestamptz NOT NULL,
    last_used_at  timestamptz,
    revoked_at    timestamptz,
    revoke_reason varchar(32),
    CONSTRAINT uq_device_credentials_secret_hash UNIQUE (secret_hash),
    CONSTRAINT ck_device_credentials_hash CHECK (secret_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_device_credentials_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_device_credentials_label CHECK (char_length(btrim(label)) BETWEEN 1 AND 64),
    CONSTRAINT ck_device_credentials_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_device_credentials_reason CHECK (
        revoke_reason IS NULL OR revoke_reason IN (
            'USER', 'PASSWORD_CHANGED', 'PASSWORD_RESET', 'ACCOUNT_INACTIVE', 'ACCOUNT_DELETION', 'EMAIL_CHANGED'
        )
    ),
    CONSTRAINT ck_device_credentials_revoked_at CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),
    CONSTRAINT ck_device_credentials_revocation CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
);

CREATE INDEX ix_device_credentials_account ON device_credentials (account_id, status);
CREATE INDEX ix_device_credentials_created ON device_credentials (account_id, created_at);
