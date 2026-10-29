-- AUTH-19: Auth'un diğer servislere ilettiği olaylar için transactional outbox. Olay, iş değişikliğiyle aynı işlemde yazılır;
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
