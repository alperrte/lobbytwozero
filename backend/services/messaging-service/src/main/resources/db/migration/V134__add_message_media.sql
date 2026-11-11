-- LTZ-UMT-MSG-04: sohbette görsel/GIF yükleme (MSG-17), GIF platformu (MSG-22). Emoji (MSG-18) için şema değişmez:
-- body UTF-8 text olarak zaten emoji saklar.

-- Mesaj türleri: TEXT (metin), IMAGE (yüklenmiş görsel; PNG, JPEG ya da GIF dosyası), GIF (GIF platformundan seçilen).
-- Görsel ve GIF mesajında body isteğe bağlı açıklamadır; metin mesajında zorunludur.
alter table messages
    alter column body drop not null,
    drop constraint ck_messages_type,
    drop constraint ck_messages_body;

-- GIF platformu kaydı mesajın üzerindedir (dosya saklanmaz; adres sağlayıcının yanıtından gelir, istemciden değil).
alter table messages
    add column gif_provider    varchar(16),
    add column gif_id          varchar(64),
    add column gif_url         varchar(512),
    add column gif_preview_url varchar(512),
    add column gif_width       int,
    add column gif_height      int,
    add constraint ck_messages_type check (type in ('TEXT', 'IMAGE', 'GIF')),
    add constraint ck_messages_body check (body is null or char_length(body) between 1 and 4000),
    add constraint ck_messages_text_body check (type <> 'TEXT' or body is not null),
    add constraint ck_messages_gif check (
        (type = 'GIF'
            and gif_provider in ('GIPHY')
            and gif_id is not null
            and gif_url like 'https://%'
            and gif_preview_url like 'https://%'
            and gif_width between 1 and 4096
            and gif_height between 1 and 4096)
        or (type <> 'GIF'
            and gif_provider is null and gif_id is null and gif_url is null and gif_preview_url is null
            and gif_width is null and gif_height is null));

-- Yüklenen görsel (MSG-17). Ayrı medya servisi ve dosya sistemi yoktur: içerik bytea olarak bu tabloda (community
-- post_images örneği). Yükleme önce mesaja bağlanmadan (message_id null) kaydedilir; mesaj gönderilince bağlanır.
-- Bağlanmayan kayıtlar süre dolunca silinir. Sohbet ya da mesaj silinince cascade ile gider.
create table message_attachments (
    id              uuid         not null,
    conversation_id uuid         not null,
    -- Yükleyen başka servise (auth/user) aittir: REF, FK yok.
    uploader_id     uuid         not null,
    message_id      uuid,
    mime_type       varchar(32)  not null,
    size_bytes      int          not null,
    width           int          not null,
    height          int          not null,
    frame_count     int          not null,
    content         bytea        not null,
    created_at      timestamptz  not null,
    constraint pk_message_attachments primary key (id),
    constraint fk_message_attachments_conversation foreign key (conversation_id)
        references conversations (id) on delete cascade,
    constraint fk_message_attachments_message foreign key (message_id)
        references messages (id) on delete cascade,
    -- Bir mesajın tek görseli olur; bir görsel tek mesaja bağlanır.
    constraint uq_message_attachments_message unique (message_id),
    constraint ck_message_attachments_mime check (mime_type in ('image/png', 'image/jpeg', 'image/gif')),
    -- Azami 1 MB (1048576 bayt, MSG-17); sayılan boyut saklanan içerikle aynı olmalı.
    constraint ck_message_attachments_size check (size_bytes between 1 and 1048576
        and octet_length(content) = size_bytes),
    constraint ck_message_attachments_dimensions check (width between 1 and 4096 and height between 1 and 4096),
    constraint ck_message_attachments_frames check (frame_count between 1 and 500)
);

-- Kullanıcı başına yükleme oranı ve bekleyen yükleme sayısı.
create index ix_message_attachments_uploader_created on message_attachments (uploader_id, created_at);
-- Süresi dolan bağlanmamış yüklemelerin temizliği.
create index ix_message_attachments_unlinked on message_attachments (created_at) where message_id is null;
