-- GAME-11: oyuna bağlı içerik türleri (GUIDES, EASTER_EGGS, EXPERIENCES) ve başlık.
-- Numara notu: Community aralığı (V91-V100) doldu. Kullanıcı 2026-11-07'de V181-V190 aralığının Community'ye
-- tahsisini açıkça onayladı; merkezî karar kaydı docs/decisions/migrations.md'ye ayrıca işlenecek.
--
-- Mevcut gönderiler etkilenmez: content_type ve title NULL kalır (eski gönderilere geriye dönük tür atanmaz).
-- posts.category (GENERAL/GUIDE/QUESTION/SCREENSHOT) ayrı bir kavramdır ve değişmez; content_type onunla eşlenmez.

alter table posts
    add column content_type varchar(32),
    add column title        varchar(120);

alter table posts add constraint ck_posts_content_type
    check (content_type is null or content_type in ('GUIDES', 'EASTER_EGGS', 'EXPERIENCES'));

-- Başlık yalnız türlü içerikte bulunur ve en az 1, en çok 120 karakterdir.
alter table posts add constraint ck_posts_title
    check ((content_type is null and title is null)
        or (content_type is not null and title is not null and char_length(btrim(title)) between 1 and 120));

-- Türlü içerik bir oyuna bağlıdır ve repost olamaz (içerik orijinale aittir).
alter table posts add constraint ck_posts_content_type_rules
    check (content_type is null or (game_id is not null and repost_of_id is null));

-- Oyun + tür listesi: yayımlanmış (VISIBLE) türlü içerik, publishedAt (created_at) azalan, eşitlikte id azalan.
create index ix_posts_visible_game_content on posts (game_id, content_type, created_at desc, id desc)
    where status = 'VISIBLE' and content_type is not null;
