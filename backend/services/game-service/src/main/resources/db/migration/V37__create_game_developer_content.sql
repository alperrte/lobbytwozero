-- GAME-13: geliştirici içerikleri. Katalog (game_media/games.description) alanlarından ayrıdır;
-- sağlayıcı verisi ve geliştirici içeriği birbirini ezmez. author_id başka servise ait REF'tir.
create table game_developer_contents (
    game_id uuid primary key references games(id) on delete cascade,
    description varchar(8000) not null check (length(trim(description)) > 0),
    author_id uuid not null,
    status varchar(16) not null check (status in ('VISIBLE','HIDDEN')),
    moderated_by uuid,
    moderation_reason varchar(1000),
    moderated_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);
create table game_developer_media (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    author_id uuid not null,
    type varchar(16) not null check (type in ('IMAGE','VIDEO','DEMO')),
    url varchar(1024) not null check (url ~ '^https://[^[:space:]]+$'),
    alt_text varchar(200),
    status varchar(16) not null check (status in ('VISIBLE','HIDDEN','DELETED')),
    moderated_by uuid,
    moderation_reason varchar(1000),
    moderated_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);
create index ix_game_developer_media_game on game_developer_media(game_id, created_at, id);
create table game_update_notes (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    author_id uuid not null,
    title varchar(120) not null check (length(trim(title)) > 0),
    body varchar(4000) not null check (length(trim(body)) > 0),
    status varchar(16) not null check (status in ('VISIBLE','HIDDEN','DELETED')),
    moderated_by uuid,
    moderation_reason varchar(1000),
    moderated_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);
create index ix_game_update_notes_game on game_update_notes(game_id, created_at desc, id);
