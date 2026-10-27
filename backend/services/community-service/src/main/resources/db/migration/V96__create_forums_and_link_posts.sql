-- COM-19: oyun forumları / konuları.
-- Forum, bir oyuna (game_id, game-service REF, FK yok) ya da genel bir konuya bağlı tartışma alanıdır; forumdaki konular
-- posts.forum_id ile bağlanan gönderilerdir ve yorumlar mevcut comments tablosunda tutulur (ayrı konu/yorum modeli yok).
-- slug ve game_id oluşturulduktan sonra değişmez (oyun ilişkisi korunur). Forum oluşturma/gizleme moderatör/admin
-- yetkisi ister; created_by başka servise (auth) ait mantıksal referanstır (REF).

create table forums (
    id          uuid          not null,
    game_id     uuid,
    slug        varchar(80)   not null,
    title       varchar(120)  not null,
    description varchar(1000),
    status      varchar(32)   not null default 'VISIBLE',
    created_by  uuid          not null,
    created_at  timestamptz   not null,
    updated_at  timestamptz   not null,
    version     bigint        not null default 0,
    constraint pk_forums primary key (id),
    constraint uq_forums_slug unique (slug),
    constraint ck_forums_slug check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and char_length(slug) between 3 and 80),
    constraint ck_forums_title check (char_length(btrim(title)) between 1 and 120),
    constraint ck_forums_status check (status in ('VISIBLE', 'HIDDEN'))
);

-- Herkese açık forum listesi (eskiden yeniye keyset) ve oyuna göre filtre.
create index ix_forums_visible_created_at on forums (created_at, id) where status = 'VISIBLE';
create index ix_forums_visible_game_id on forums (game_id, created_at, id) where status = 'VISIBLE' and game_id is not null;

-- Forum konuları: posts.forum_id (null = forum dışı gönderi). Forum fiziksel silinmez (RESTRICT).
alter table posts add column forum_id uuid;
alter table posts add constraint fk_posts_forum foreign key (forum_id) references forums (id) on delete restrict;
create index ix_posts_visible_forum_created_at on posts (forum_id, created_at desc, id desc)
    where status = 'VISIBLE' and forum_id is not null;
