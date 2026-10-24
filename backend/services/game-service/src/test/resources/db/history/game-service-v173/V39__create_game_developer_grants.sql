-- GAME-12: oyun bazlı geliştirici yetkisi. developer_user_id başka servise ait REF'tir (FK yok).
create table game_developer_grants (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    developer_user_id uuid not null,
    granted_by uuid not null,
    granted_at timestamptz not null,
    revoked_by uuid,
    revoked_at timestamptz,
    version bigint not null default 0,
    constraint ck_game_developer_grants_revocation check ((revoked_at is null) = (revoked_by is null))
);
create unique index uq_game_developer_grants_active on game_developer_grants(game_id, developer_user_id)
    where revoked_at is null;
create index ix_game_developer_grants_user on game_developer_grants(developer_user_id) where revoked_at is null;
