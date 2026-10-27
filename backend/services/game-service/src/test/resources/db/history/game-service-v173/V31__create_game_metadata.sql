create table games (
    id uuid primary key,
    title varchar(256) not null check (length(trim(title)) > 0),
    normalized_title varchar(256) not null check (length(trim(normalized_title)) > 0),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);
create table catalog_terms (
    id uuid primary key,
    kind varchar(32) not null check (kind in ('GENRE','CATEGORY','TAG','PLATFORM')),
    code varchar(64) not null check (length(trim(code)) > 0),
    name_tr varchar(128),
    name_en varchar(128),
    version bigint not null default 0,
    constraint uq_catalog_terms_kind_code unique(kind, code)
);
create table game_terms (
    game_id uuid not null references games(id) on delete cascade,
    term_id uuid not null references catalog_terms(id) on delete restrict,
    primary key(game_id, term_id)
);
create index ix_game_terms_term_id on game_terms(term_id);
create table game_developers (
    game_id uuid not null references games(id) on delete cascade,
    name varchar(256) not null check (length(trim(name)) > 0),
    primary key(game_id, name)
);
create table game_publishers (
    game_id uuid not null references games(id) on delete cascade,
    name varchar(256) not null check (length(trim(name)) > 0),
    primary key(game_id, name)
);
create table game_media (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    type varchar(32) not null check (type in ('COVER','SCREENSHOT','TRAILER')),
    url varchar(2048) not null check (url ~ '^https://[^[:space:]]+$'),
    provider varchar(32),
    sort_order integer not null check (sort_order >= 0),
    version bigint not null default 0
);
create index ix_game_media_game_id on game_media(game_id);
create table game_system_requirements (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    level varchar(32) not null check (level in ('MINIMUM','RECOMMENDED')),
    platform varchar(64) not null check (length(trim(platform)) > 0),
    os varchar(512), cpu varchar(512), gpu varchar(512),
    ram_mb integer check (ram_mb >= 0),
    storage_mb integer check (storage_mb >= 0),
    raw_text varchar(16000),
    version bigint not null default 0,
    constraint uq_game_system_requirements_game_platform_level unique(game_id, platform, level)
);
