-- AUTH-16: güvenli hesap silme ve servis koordinasyonu.

-- E-postayla gönderilen tek kullanımlık silme onay token'ı; yalnız SHA-256 özeti saklanır.
CREATE TABLE account_deletion_tokens (
    id                  uuid        PRIMARY KEY,
    account_id          uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    token_hash          varchar(64) NOT NULL,
    created_at          timestamptz NOT NULL,
    expires_at          timestamptz NOT NULL,
    consumed_at         timestamptz,
    invalidated_at      timestamptz,
    invalidation_reason varchar(32),
    CONSTRAINT uq_account_deletion_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_account_deletion_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_account_deletion_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_account_deletion_tokens_reason CHECK (
        invalidation_reason IS NULL OR invalidation_reason IN ('SUPERSEDED', 'DELIVERY_FAILED')),
    CONSTRAINT ck_account_deletion_tokens_invalidation CHECK ((invalidated_at IS NULL) = (invalidation_reason IS NULL)),
    CONSTRAINT ck_account_deletion_tokens_single_outcome CHECK (consumed_at IS NULL OR invalidated_at IS NULL)
);

CREATE UNIQUE INDEX uq_account_deletion_tokens_active
    ON account_deletion_tokens (account_id)
    WHERE consumed_at IS NULL AND invalidated_at IS NULL;

CREATE INDEX ix_account_deletion_tokens_issued ON account_deletion_tokens (account_id, created_at);

-- Silme talebi: hesap başına tek (idempotent). Hesap satırı anonimleştirilip korunduğundan FK CASCADE değildir.
CREATE TABLE account_deletion_requests (
    id           uuid        PRIMARY KEY,
    account_id   uuid        NOT NULL REFERENCES accounts (id),
    status       varchar(16) NOT NULL,
    requested_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT uq_account_deletion_requests_account UNIQUE (account_id),
    CONSTRAINT ck_account_deletion_requests_status CHECK (status IN ('COORDINATING', 'COMPLETED')),
    CONSTRAINT ck_account_deletion_requests_completion CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

-- Hizmet başına silme görevi. Görev, ilgili servis silmeyi idempotent biçimde onayladığında CONFIRMED olur.
CREATE TABLE account_deletion_tasks (
    id              uuid        PRIMARY KEY,
    request_id      uuid        NOT NULL REFERENCES account_deletion_requests (id),
    account_id      uuid        NOT NULL,
    service         varchar(32) NOT NULL,
    status          varchar(16) NOT NULL,
    attempts        integer     NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    last_error      varchar(64),
    created_at      timestamptz NOT NULL,
    confirmed_at    timestamptz,
    CONSTRAINT uq_account_deletion_tasks_service UNIQUE (request_id, service),
    CONSTRAINT ck_account_deletion_tasks_service CHECK (service ~ '^[a-z0-9-]{1,32}$'),
    CONSTRAINT ck_account_deletion_tasks_status CHECK (status IN ('PENDING', 'CONFIRMED')),
    CONSTRAINT ck_account_deletion_tasks_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_account_deletion_tasks_confirmation CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL))
);

CREATE INDEX ix_account_deletion_tasks_due ON account_deletion_tasks (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_account_deletion_tasks_request ON account_deletion_tasks (request_id, status);

ALTER TABLE auth_sessions DROP CONSTRAINT ck_auth_sessions_reason;
ALTER TABLE auth_sessions ADD CONSTRAINT ck_auth_sessions_reason CHECK (
    revoke_reason IS NULL OR revoke_reason IN (
        'LOGOUT', 'LOGOUT_ALL', 'REFRESH_REUSE', 'ACCOUNT_INACTIVE', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'ROLE_CHANGED', 'ACCOUNT_DELETION'
    )
);
