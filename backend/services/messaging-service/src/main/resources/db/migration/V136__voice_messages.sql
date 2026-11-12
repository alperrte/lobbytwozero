-- LTZ-UMT-MSG-06: sesli mesaj (MSG-21). Ses, görselin yükleme/bağlama/iletme/silme altyapısını kullanır
-- (message_attachments + attachment_blobs); yeni tablo yoktur. Sunucu yalnız Opus paketlerini alıp WebM olarak yeniden
-- paketler: saklanan tür her zaman audio/webm'dir.

-- VOICE mesajında açıklama (body) yoktur.
alter table messages
    drop constraint ck_messages_type,
    add constraint ck_messages_type check (type in ('TEXT', 'IMAGE', 'GIF', 'VOICE')),
    add constraint ck_messages_voice_body check (type <> 'VOICE' or body is null);

-- Görselin ölçüleri seste anlamsızdır; ses süre (içerikten hesaplanır) ve isteğe bağlı 64 değerlik dalga formu taşır.
alter table message_attachments
    alter column width drop not null,
    alter column height drop not null,
    alter column frame_count drop not null,
    add column duration_ms int,
    add column waveform    bytea,
    drop constraint ck_message_attachments_mime,
    add constraint ck_message_attachments_mime
        check (mime_type in ('image/png', 'image/jpeg', 'image/gif', 'audio/webm')),
    drop constraint ck_message_attachments_dimensions,
    drop constraint ck_message_attachments_frames,
    add constraint ck_message_attachments_kind check (
        (mime_type like 'image/%'
            -- CHECK NULL'da geçer: zorunlu alanlar açıkça "is not null" ile denetlenir.
            and width is not null and height is not null and frame_count is not null
            and width between 1 and 4096
            and height between 1 and 4096
            and frame_count between 1 and 500
            and duration_ms is null
            and waveform is null)
        or (mime_type = 'audio/webm'
            and width is null and height is null and frame_count is null
            -- 0,5 sn - 2 dk + 1 sn tolerans (ltz.messaging.voice.*)
            and duration_ms is not null and duration_ms between 500 and 121000
            and (waveform is null or octet_length(waveform) = 64)));
