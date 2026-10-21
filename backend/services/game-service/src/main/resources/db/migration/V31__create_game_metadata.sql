-- Ba?lang?? ?emas?: katalog, metadata, dil listesi ve ke?if indeksleri.
create table games (
    id uuid primary key,
    title varchar(256) not null check (length(trim(title)) > 0),
    normalized_title varchar(1024) not null check (length(trim(normalized_title)) > 0),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    origin varchar(32) not null default 'INTERNAL'
    check (origin in ('INTERNAL','EXTERNAL')),
    metadata_completeness varchar(32) not null default 'INDEX_ONLY'
    check (metadata_completeness in ('INDEX_ONLY','PARTIAL','FULL')),
    status varchar(32) not null default 'ACTIVE'
    check (status in ('ACTIVE','ARCHIVED','MERGED')),
    game_type varchar(32) not null default 'UNKNOWN'
    check(game_type in ('MAIN_GAME','DLC','DEMO','SOUNDTRACK','SOFTWARE','VIDEO','PACKAGE','HARDWARE','UNKNOWN')),
    description varchar(16000),
    release_date date,
    details_fetched_at timestamptz,
    details_expires_at timestamptz,
    details_next_attempt_at timestamptz,
    details_failure_count integer not null default 0 check (details_failure_count >= 0),
    details_last_error_code varchar(64),
    early_access boolean,
    editor_rank integer check (editor_rank between 1 and 100),
    popularity_updated_at timestamptz,
    editor_actor_id uuid,
    editor_evidence varchar(1000),
    constraint uq_games_editor_rank unique (editor_rank) deferrable initially deferred,
    metadata_language varchar(35),
    language_data_state varchar(16) not null default 'UNKNOWN'
        check(language_data_state in ('UNKNOWN','EMPTY','INVALID','AVAILABLE'))
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

create table game_languages (
    game_id uuid not null references games(id) on delete cascade,
    language_code varchar(35) not null check (language_code ~ '^[a-z]{2,3}(-[a-z0-9]{2,8}){0,3}$'),
    primary key (game_id, language_code)
);
create index ix_game_languages_language on game_languages(language_code, game_id);
create index ix_games_normalized_title on games(normalized_title varchar_pattern_ops, id);
create index ix_games_active_type_title on games(game_type, normalized_title varchar_pattern_ops, id) where status='ACTIVE';
create index ix_games_discovery_title on games(normalized_title varchar_pattern_ops, id)
    where status = 'ACTIVE' and game_type = 'MAIN_GAME';
create index ix_games_discovery_release on games(release_date, id)
    where status = 'ACTIVE' and game_type = 'MAIN_GAME';
create index ix_game_media_cover_order on game_media(game_id, type, sort_order, id);
