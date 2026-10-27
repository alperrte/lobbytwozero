-- GAME-11: yalnız tanımlar; oyuncu ilerlemesi veya başka servisin UGC verisi yoktur.
create table game_achievement_definitions (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    code varchar(64) not null,
    title varchar(120) not null,
    description varchar(2000),
    visible boolean not null default true,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_game_achievement_code unique(game_id, code)
);
create index ix_game_achievement_public on game_achievement_definitions(game_id, visible, code, id);
