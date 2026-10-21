-- AUTH-02: 6 haneli doğrulama kodları. Kodun kendisi saklanmaz; yalnız HMAC-SHA256 özeti (hex) tutulur.

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
