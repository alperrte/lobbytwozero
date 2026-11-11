-- LTZ-UMT-MSG-05: mesaja yanıt (MSG-19), iletme (MSG-20), tepki (MSG-23), benden sil (MSG-25), herkesten sil (MSG-26).

-- Görsel içeriği ayrı tabloya taşınır: iletilen görsel baytları kopyalamaz, aynı içeriğe yeni bir bağlantı satırı açar
-- (MSG-20). İçerik, ona bağlı son bağlantı satırı da kalkınca silinir (uygulama + temizlik işi).
create table attachment_blobs (
    id         uuid        not null,
    size_bytes int         not null,
    content    bytea       not null,
    created_at timestamptz not null,
    constraint pk_attachment_blobs primary key (id),
    -- Azami 1 MB (MSG-17); sayılan boyut saklanan içerikle aynı olmalı.
    constraint ck_attachment_blobs_size check (size_bytes between 1 and 1048576 and octet_length(content) = size_bytes)
);

insert into attachment_blobs (id, size_bytes, content, created_at)
select id, size_bytes, content, created_at from message_attachments;

alter table message_attachments
    add column blob_id   uuid,
    -- İletmeyle açılan bağlantı: yükleme oranı ve bekleyen yükleme sınırına sayılmaz.
    add column forwarded boolean not null default false;

update message_attachments set blob_id = id;

alter table message_attachments
    alter column blob_id set not null,
    add constraint fk_message_attachments_blob foreign key (blob_id)
        references attachment_blobs (id) on delete restrict,
    drop constraint ck_message_attachments_size,
    add constraint ck_message_attachments_size check (size_bytes between 1 and 1048576),
    drop column content;

create index ix_message_attachments_blob on message_attachments (blob_id);

-- Yükleme oranı yalnız gerçek yüklemeleri sayar.
drop index ix_message_attachments_uploader_created;
create index ix_message_attachments_uploader_created on message_attachments (uploader_id, created_at)
    where forwarded = false;

-- Yanıt, iletme sayacı ve herkesten silme. Mesaj satırı silinmez (seq, okunmamış ve okundu bilgisi bozulmasın);
-- herkesten silmede içerik alanları temizlenir.
alter table messages
    add column reply_to_message_id uuid,
    add column forward_count       int not null default 0,
    add column deleted_at          timestamptz,
    add column deleted_by          uuid,
    add constraint fk_messages_reply_to foreign key (reply_to_message_id)
        references messages (id) on delete set null,
    add constraint ck_messages_forward_count check (forward_count >= 0),
    add constraint ck_messages_deleted_by check ((deleted_at is null) = (deleted_by is null)),
    add constraint ck_messages_deleted_content check (deleted_at is null or body is null),
    drop constraint ck_messages_text_body,
    add constraint ck_messages_text_body check (type <> 'TEXT' or body is not null or deleted_at is not null),
    drop constraint ck_messages_gif,
    add constraint ck_messages_gif check (
        (type = 'GIF' and deleted_at is null
            and gif_provider in ('GIPHY')
            and gif_id is not null
            and gif_url like 'https://%'
            and gif_preview_url like 'https://%'
            and gif_width between 1 and 4096
            and gif_height between 1 and 4096)
        or ((type <> 'GIF' or deleted_at is not null)
            and gif_provider is null and gif_id is null and gif_url is null and gif_preview_url is null
            and gif_width is null and gif_height is null));

-- Hesap silme ya da sohbet silinmesinde yanıt referansının SET NULL taraması için.
create index ix_messages_reply_to on messages (reply_to_message_id) where reply_to_message_id is not null;

-- Tepkiler (MSG-23): kişi başına mesaj başına tek tepki.
create table message_reactions (
    message_id uuid        not null,
    -- Tepki veren başka servise (auth/user) aittir: REF, FK yok.
    user_id    uuid        not null,
    emoji      varchar(32) not null,
    reacted_at timestamptz not null,
    constraint pk_message_reactions primary key (message_id, user_id),
    constraint fk_message_reactions_message foreign key (message_id) references messages (id) on delete cascade,
    constraint ck_message_reactions_emoji check (char_length(emoji) between 1 and 32)
);

-- Hesap silmede kullanıcının tepkileri.
create index ix_message_reactions_user on message_reactions (user_id);

-- Benden sil (MSG-25): mesaj yalnız bu kullanıcının görünümünden kalkar.
create table message_hidden_for_users (
    -- Gizleyen başka servise (auth/user) aittir: REF, FK yok.
    user_id    uuid        not null,
    message_id uuid        not null,
    hidden_at  timestamptz not null,
    constraint pk_message_hidden_for_users primary key (user_id, message_id),
    constraint fk_message_hidden_message foreign key (message_id) references messages (id) on delete cascade
);

-- Mesaj başına gizleyenler (canlı olay alıcıları, cascade).
create index ix_message_hidden_message on message_hidden_for_users (message_id);
