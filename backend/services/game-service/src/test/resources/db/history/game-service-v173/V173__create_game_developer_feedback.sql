-- GAME-14: özel oyuncu -> oyun geliştiricisi iş akışı; Review verisi değildir.
create table game_developer_feedback (
    id uuid primary key,
    game_id uuid not null references games(id) on delete restrict,
    author_id uuid not null,
    type varchar(24) not null check(type in ('GENERAL','BUG_REPORT','EARLY_ACCESS')),
    status varchar(24) not null default 'OPEN' check(status in ('OPEN','IN_PROGRESS','RESOLVED')),
    title varchar(120) not null,
    body varchar(4000) not null,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index ix_game_feedback_author on game_developer_feedback(author_id,created_at desc,id);
create index ix_game_feedback_game on game_developer_feedback(game_id,type,status,created_at desc,id);
