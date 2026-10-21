-- COM-01: topluluk gönderileri.
-- author_id ve game_id başka servislere (auth/user, game) ait mantıksal referanstır (REF); FK yoktur.
-- game_id isteğe bağlıdır; doğrulama ve API alanı COM-14'te eklenir (V91-V100 aralığını korumak için kolon burada açıldı).

create table posts (
    id         uuid        not null,
    author_id  uuid        not null,
    game_id    uuid,
    body       text        not null,
    status     varchar(32) not null default 'VISIBLE',
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz,
    version    bigint      not null default 0,
    constraint pk_posts primary key (id),
    constraint ck_posts_status check (status in ('VISIBLE', 'HIDDEN', 'DELETED')),
    constraint ck_posts_body_length check (char_length(btrim(body)) between 1 and 5000),
    constraint ck_posts_deleted_at check ((status = 'DELETED') = (deleted_at is not null))
);

create index ix_posts_author_id_created_at on posts (author_id, created_at desc);
