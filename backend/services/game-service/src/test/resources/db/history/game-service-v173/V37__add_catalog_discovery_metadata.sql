alter table games
    add column early_access boolean,
    add column editor_rank integer check (editor_rank between 1 and 100),
    add column popularity_updated_at timestamptz,
    add column editor_actor_id uuid,
    add column editor_evidence varchar(1000),
    add constraint uq_games_editor_rank unique (editor_rank) deferrable initially deferred;

create table game_languages (
    game_id uuid not null references games(id) on delete cascade,
    language_code varchar(35) not null check (language_code ~ '^[a-z]{2,3}(-[a-z0-9]{2,8}){0,3}$'),
    primary key (game_id, language_code)
);
create index ix_game_languages_language on game_languages(language_code, game_id);
create index ix_games_discovery_title on games(normalized_title varchar_pattern_ops, id)
    where status = 'ACTIVE' and game_type = 'MAIN_GAME';
create index ix_games_discovery_release on games(release_date, id)
    where status = 'ACTIVE' and game_type = 'MAIN_GAME';
create index ix_game_media_cover_order on game_media(game_id, type, sort_order, id);
