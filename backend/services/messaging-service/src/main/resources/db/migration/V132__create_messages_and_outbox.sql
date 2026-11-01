-- LTZ-UMT-MSG-02: metin mesajları (MSG-06/07/10), sohbet listesi ve okunmamış sayısı (MSG-08/13), gerçek zamanlı
-- iletim için kayıpsız sıra (MSG-09) ve mesaj olaylarının outbox'a yazılması (MSG-16).

-- Sohbet başına artan mesaj sırası. Mesaj, sohbet satırı kilitliyken last_message_seq + 1 alır; böylece bir sohbette
-- seq sırası commit sırasıyla aynıdır ve "seq > X" sorgusu arada commit edilmiş mesajı kaçırmaz.
alter table conversations
    add column last_message_seq bigint not null default 0,
    add column last_activity_at timestamptz;

update conversations set last_activity_at = created_at;

alter table conversations
    alter column last_activity_at set not null,
    add constraint ck_conversations_last_message_seq check (last_message_seq >= 0);

-- visible_from_seq: üyenin görebildiği ilk mesaj (katılmadan önceki mesajlar görünmez).
-- last_read_seq: üyenin okuduğunu bildirdiği son mesaj; okunmamış sayısı buradan hesaplanır (MSG-13).
alter table conversation_members
    add column visible_from_seq bigint not null default 1,
    add column last_read_seq bigint not null default 0,
    add constraint ck_conversation_members_visible_from check (visible_from_seq >= 1),
    add constraint ck_conversation_members_last_read check (last_read_seq >= 0);

create table messages (
    id                uuid         not null,
    conversation_id   uuid         not null,
    seq               bigint       not null,
    -- Gönderen başka servise (auth/user) aittir: REF, FK yok.
    sender_id         uuid         not null,
    -- İstemcinin ürettiği tekil kimlik; ağ yeniden denemesi çift mesaj oluşturmaz (SECURITY.md §6).
    client_message_id varchar(64)  not null,
    type              varchar(32)  not null,
    body              text         not null,
    created_at        timestamptz  not null,
    constraint pk_messages primary key (id),
    constraint fk_messages_conversation foreign key (conversation_id)
        references conversations (id) on delete cascade,
    constraint uq_messages_conversation_seq unique (conversation_id, seq),
    constraint uq_messages_sender_client_message unique (sender_id, client_message_id),
    constraint ck_messages_seq check (seq >= 1),
    -- Medya türleri (MSG-17/22) kendi görevlerinde eklenecek.
    constraint ck_messages_type check (type in ('TEXT')),
    constraint ck_messages_body check (char_length(body) between 1 and 4000)
);

-- Kullanıcı başına gönderim oranı sınırı (SECURITY.md §12).
create index ix_messages_sender_created on messages (sender_id, created_at);

-- Transactional outbox (docs/database.md §2.4): olay mesajla aynı transaction'da yazılır.
create table outbox_events (
    id              uuid         not null,
    aggregate_type  varchar(64)  not null,
    aggregate_id    uuid         not null,
    event_type      varchar(128) not null,
    event_version   int          not null default 1,
    payload         jsonb        not null,
    occurred_at     timestamptz  not null,
    status          varchar(16)  not null default 'PENDING',
    attempts        int          not null default 0,
    next_attempt_at timestamptz,
    sent_at         timestamptz,
    constraint pk_outbox_events primary key (id),
    constraint ck_outbox_events_status check (status in ('PENDING', 'SENT', 'FAILED')),
    constraint ck_outbox_events_attempts check (attempts >= 0)
);

create index ix_outbox_events_status_next on outbox_events (status, next_attempt_at);
