-- CONT-05/06/07/08/18: haber, resmi duyuru ve kullanıcı içeriği; bu fazda yalnız taslak (DRAFT) oluşturulur.
-- author_id başka servise (auth/user) ait mantıksal referanstır (REF); FK yoktur.
-- Durum kümesi docs/architecture.md durum makinesiyle aynıdır (DRAFT, PUBLISHED, REMOVED, ARCHIVED); yayınlama, kaldırma ve
-- arşivleme sonraki fazlarda gelir, CHECK şimdiden tam kümeyi içerir (kısıtı sonradan genişletmek için ALTER gerekmesin).
-- Silme politikası (docs/database.md §2.3): kullanıcı içeriği soft delete (deleted_at) ile kaldırılır; silinmiş içerik
-- hiçbir okuma yolunda görünmez. Ayrı bir DELETED durumu yoktur.
-- content_hash: (yazar, tür) başına aynı metnin ikinci kez kaydedilmesini (mükerrer paylaşım, CONT-18) veritabanı
-- düzeyinde engeller; yarışta da çalışsın diye kısmi tekil indeks kullanılır.
-- default_locale: içeriğin ilk (şimdilik tek) çevirisinin dili; çoklu dil desteği (CONT-15) bu tabloya çeviri ekler.

create table contents (
    id             uuid         not null,
    author_id      uuid         not null,
    type           varchar(32)  not null,
    status         varchar(32)  not null default 'DRAFT',
    default_locale varchar(5)   not null default 'tr',
    content_hash   varchar(64)  not null,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    deleted_at     timestamptz,
    version        bigint       not null default 0,
    constraint pk_contents primary key (id),
    constraint ck_contents_type check (type in ('NEWS', 'ANNOUNCEMENT', 'USER_CONTENT')),
    constraint ck_contents_status check (status in ('DRAFT', 'PUBLISHED', 'REMOVED', 'ARCHIVED')),
    constraint ck_contents_default_locale check (default_locale in ('tr', 'en')),
    constraint ck_contents_content_hash check (char_length(content_hash) = 64)
);

-- Yazar başına listeleme ve saatlik oluşturma sınırı (silinmiş satırlar da sayılır; sil-yeniden-oluştur ile aşılamaz).
create index ix_contents_author_id_created_at on contents (author_id, created_at desc);

create unique index uq_contents_author_id_type_content_hash
    on contents (author_id, type, content_hash)
    where deleted_at is null;

create table content_translations (
    content_id uuid         not null,
    locale     varchar(5)   not null,
    title      varchar(150) not null,
    summary    varchar(500),
    body       text,
    constraint pk_content_translations primary key (content_id, locale),
    constraint fk_content_translations_content foreign key (content_id) references contents (id) on delete cascade,
    constraint ck_content_translations_locale check (locale in ('tr', 'en')),
    constraint ck_content_translations_title check (char_length(btrim(title)) between 1 and 150),
    constraint ck_content_translations_summary check (summary is null or char_length(btrim(summary)) between 1 and 500),
    constraint ck_content_translations_body check (body is null or char_length(btrim(body)) between 1 and 20000)
);
