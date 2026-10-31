-- AUTH-16/19: diğer servislerle koordinasyon. Auth hesap silmeyi ve olay yayınını bu tablolarla yürütür.
--
-- İçindekiler
--   1. Hesap silme  : account_deletion_tokens, account_deletion_requests, account_deletion_tasks
--   2. Olay kuyruğu : auth_event_outbox

-- =====================================================================================================================
-- 1. HESAP SİLME
-- =====================================================================================================================

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
-- Geri alma (bekleme) süresi: talep scheduled_for anına kadar iptal edilebilir; görevler de bu andan önce çalışmaz
-- (account_deletion_tasks.next_attempt_at = scheduled_for).
CREATE TABLE account_deletion_requests (
    id            uuid        PRIMARY KEY,
    account_id    uuid        NOT NULL REFERENCES accounts (id),
    status        varchar(16) NOT NULL,
    requested_at  timestamptz NOT NULL,
    completed_at  timestamptz,
    scheduled_for timestamptz NOT NULL,
    cancelled_at  timestamptz,
    CONSTRAINT uq_account_deletion_requests_account UNIQUE (account_id),
    CONSTRAINT ck_account_deletion_requests_status CHECK (status IN ('COORDINATING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_account_deletion_requests_completion CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_account_deletion_requests_cancellation CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT ck_account_deletion_requests_schedule CHECK (scheduled_for >= requested_at)
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

-- =====================================================================================================================
-- 2. OLAY KUYRUĞU
-- =====================================================================================================================

-- Auth'un diğer servislere ilettiği olaylar için transactional outbox. Olay, iş değişikliğiyle aynı işlemde yazılır;
-- hedef başına bir satır. Satırlar kişisel veri içermez (hesap kimliği, durum veya herkese açık kullanıcı adı).
-- Hedef listesi boşsa (varsayılan) hiç satır yazılmaz. Hesap silinince satırlar CASCADE ile gider.
CREATE TABLE auth_event_outbox (
    id              uuid        PRIMARY KEY,
    event_id        uuid        NOT NULL,
    account_id      uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    event_type      varchar(32) NOT NULL,
    payload_value   varchar(64),
    target          varchar(32) NOT NULL,
    status          varchar(16) NOT NULL,
    attempts        integer     NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    last_error      varchar(64),
    occurred_at     timestamptz NOT NULL,
    created_at      timestamptz NOT NULL,
    delivered_at    timestamptz,
    CONSTRAINT uq_auth_event_outbox_delivery UNIQUE (event_id, target),
    CONSTRAINT ck_auth_event_outbox_type CHECK (
        event_type IN ('ACCOUNT_STATUS_CHANGED', 'USER_REGISTERED', 'ACCOUNT_EMAIL_CHANGED')
    ),
    CONSTRAINT ck_auth_event_outbox_target CHECK (target ~ '^[a-z0-9][a-z0-9-]{0,31}$'),
    CONSTRAINT ck_auth_event_outbox_status CHECK (status IN ('PENDING', 'DELIVERED')),
    CONSTRAINT ck_auth_event_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_auth_event_outbox_delivery CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL))
);

CREATE INDEX ix_auth_event_outbox_due ON auth_event_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_auth_event_outbox_delivered ON auth_event_outbox (delivered_at) WHERE status = 'DELIVERED';
CREATE INDEX ix_auth_event_outbox_account ON auth_event_outbox (account_id);
