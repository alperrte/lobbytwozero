-- SOC-13: arkadaşlık olaylarının (istek, kabul, arkadaşlık değişimi) notification-service ve diğer servislerle
-- sözleşme bazlı paylaşımı için transactional outbox (docs/database.md §2.4, docs/architecture.md §6.2).
-- Olay, iş verisiyle aynı transaction'da yazılır; teslim ayrı bir relay ile ve en az bir kez yapılır.

CREATE TABLE outbox_events (
    id              uuid         PRIMARY KEY,
    aggregate_type  varchar(64)  NOT NULL,
    aggregate_id    uuid         NOT NULL,
    event_type      varchar(128) NOT NULL,
    event_version   int          NOT NULL DEFAULT 1,
    payload         jsonb        NOT NULL,
    occurred_at     timestamptz  NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'PENDING',
    attempts        int          NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    sent_at         timestamptz,
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0)
);

-- Relay'in "zamanı gelmiş bekleyen olay" sorgusu.
CREATE INDEX ix_outbox_events_status_next ON outbox_events (status, next_attempt_at);
