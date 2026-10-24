-- Faz 4 (COM-16, COM-17, COM-18, COM-21): servisler arası asenkron iletişim için teknik tablolar (docs/database.md §2.4).
-- Outbox: olay, iş verisiyle AYNI transaction'da yazılır; zamanlanmış relay bekleyen olayları tüketici servisin
-- iç endpoint'ine (POST /internal/events) iletir (en az bir kez teslim). Inbox: gelen olayları event_id ile tekilleştirir.
-- Faz 4 boyunca V98-V100 aralığını korumak için bu dosya iki tabloyu birlikte açar.

create table outbox_events (
    id              uuid         not null,
    aggregate_type  varchar(64)  not null,
    aggregate_id    uuid         not null,
    event_type      varchar(128) not null,
    event_version   integer      not null default 1,
    payload         jsonb        not null,
    occurred_at     timestamptz  not null,
    status          varchar(16)  not null default 'PENDING',
    attempts        integer      not null default 0,
    next_attempt_at timestamptz,
    sent_at         timestamptz,
    constraint pk_outbox_events primary key (id),
    constraint ck_outbox_events_status check (status in ('PENDING', 'SENT', 'FAILED')),
    constraint ck_outbox_events_attempts check (attempts >= 0)
);

-- Relay yalnız teslim zamanı gelmiş PENDING olayları tarar.
create index ix_outbox_events_status_next on outbox_events (status, next_attempt_at);

create table inbox_events (
    event_id     uuid         not null,
    event_type   varchar(128) not null,
    producer     varchar(64)  not null,
    processed_at timestamptz  not null,
    constraint pk_inbox_events primary key (event_id)
);
