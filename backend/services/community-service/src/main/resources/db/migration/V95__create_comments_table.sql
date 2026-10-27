-- COM-07 / COM-08 / COM-09: gönderi yorumları, düzenleme/silme ve yorumlara yanıt.
-- author_id başka servise (auth/user) ait mantıksal referanstır (REF); FK yoktur.
-- parent_comment_id yanıt ilişkisini korur (ürün kararı: sınırsız iç içe yanıt). Yanıtlar özyinelemeli sorguyla değil,
-- üst yorum başına ayrı uçtan (keyset sayfalı) okunduğu için derinlik sorgu maliyetini artırmaz.
-- Silme soft delete'tir (status = DELETED, deleted_at dolu); yanıtı olan silinmiş yorum, ilişki bozulmasın diye satır olarak kalır.

create table comments (
    id                uuid        not null,
    post_id           uuid        not null,
    author_id         uuid        not null,
    parent_comment_id uuid,
    body              text        not null,
    status            varchar(32) not null default 'VISIBLE',
    created_at        timestamptz not null,
    updated_at        timestamptz not null,
    deleted_at        timestamptz,
    version           bigint      not null default 0,
    constraint pk_comments primary key (id),
    constraint fk_comments_post foreign key (post_id) references posts (id) on delete restrict,
    constraint fk_comments_parent foreign key (parent_comment_id) references comments (id) on delete restrict,
    constraint ck_comments_status check (status in ('VISIBLE', 'HIDDEN', 'DELETED')),
    constraint ck_comments_body_length check (char_length(btrim(body)) between 1 and 2000),
    constraint ck_comments_deleted_at check ((status = 'DELETED') = (deleted_at is not null)),
    constraint ck_comments_not_own_parent check (parent_comment_id is null or parent_comment_id <> id)
);

-- Gönderinin üst düzey yorumları (eskiden yeniye keyset sayfalama).
create index ix_comments_post_top_level on comments (post_id, created_at, id) where parent_comment_id is null;
-- Bir yorumun yanıtları (eskiden yeniye keyset sayfalama) ve yanıt sayısı.
create index ix_comments_parent on comments (parent_comment_id, created_at, id) where parent_comment_id is not null;
-- Kullanıcı bazlı sorgular (profil/moderasyon).
create index ix_comments_author_id on comments (author_id, created_at desc);
