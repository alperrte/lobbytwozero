-- CONT-10: moderatör tarafından yayından kaldırma. Kaldırma gerekçesi, kararı veren yetkili ve zamanı kaydedilir.
-- contents.removed_*: GEÇERLİ durum (REMOVED iken dolu; geri alınınca temizlenir). content_moderation_actions: değişmez
-- işlem geçmişi (her kaldırma ve geri alma, kim/ne zaman/neden/hangi yoldan). Moderatör kimliği (actor_id) başka servise
-- (auth) ait mantıksal referanstır (REF); FK yoktur. İçerik kalıcı silinirse (hesap silme) geçmişi de cascade ile silinir.
-- inbox_events: admin-service'ten gelen ModerationActionTaken olaylarının tekilleştirilmesi (docs/database.md §2.4); olay
-- aynı transaction'da tekilleştirilir ve uygulanır, böylece en az bir kez teslim iş etkisini en fazla bir kez yaratır.

alter table contents
    add column removed_at     timestamptz,
    add column removed_by     uuid,
    add column removed_reason varchar(500);

-- REMOVED durumu ile kaldırma bilgisi birlikte vardır; gerekçe boş olamaz.
alter table contents
    add constraint ck_contents_removal check (
        (status = 'REMOVED') = (removed_at is not null)
        and (removed_at is null) = (removed_by is null)
        and (removed_at is null) = (removed_reason is null)
        and (removed_reason is null or char_length(btrim(removed_reason)) between 1 and 500));

create table content_moderation_actions (
    id         uuid         not null,
    content_id uuid         not null,
    action     varchar(16)  not null,
    actor_id   uuid         not null,
    source     varchar(16)  not null,
    reason     varchar(500) not null,
    report_id  uuid,
    event_id   uuid,
    created_at timestamptz  not null,
    constraint pk_content_moderation_actions primary key (id),
    constraint fk_content_moderation_actions_content foreign key (content_id) references contents (id) on delete cascade,
    constraint ck_content_moderation_actions_action check (action in ('REMOVE', 'RESTORE')),
    constraint ck_content_moderation_actions_source check (source in ('MODERATOR_API', 'ADMIN_SERVICE')),
    constraint ck_content_moderation_actions_reason check (char_length(btrim(reason)) between 1 and 500)
);

create index ix_content_moderation_actions_content_id_created_at
    on content_moderation_actions (content_id, created_at desc);

create table inbox_events (
    event_id     uuid         not null,
    event_type   varchar(128) not null,
    producer     varchar(64)  not null,
    processed_at timestamptz  not null,
    constraint pk_inbox_events primary key (event_id)
);
