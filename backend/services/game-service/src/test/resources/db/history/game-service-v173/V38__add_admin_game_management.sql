-- GAME-07: yetkili oyun yönetimi. Manuel düzeltmeler (alan kilidi) ve değiştirilemez yönetim denetim kaydı.
create table game_field_overrides (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    field varchar(32) not null check (field in ('TITLE','DESCRIPTION','RELEASE_DATE','GAME_TYPE','DEVELOPERS','PUBLISHERS')),
    actor_id uuid not null,
    created_at timestamptz not null,
    version bigint not null default 0,
    constraint uq_game_field_overrides_game_field unique (game_id, field)
);
create table game_admin_audits (
    id uuid primary key,
    game_id uuid references games(id) on delete restrict,
    actor_id uuid not null,
    actor_role varchar(32) not null check (actor_role in ('ADMIN','MODERATOR','GAME_DEVELOPER')),
    action varchar(48) not null check (action in ('CREATE_GAME','UPDATE_GAME','ARCHIVE_GAME','RESTORE_GAME',
        'RELEASE_OVERRIDE','SET_EDITORIAL','GRANT_DEVELOPER','REVOKE_DEVELOPER','HIDE_DEVELOPER_CONTENT','RESTORE_DEVELOPER_CONTENT')),
    reason varchar(1000) check (reason is null or length(trim(reason)) >= 8),
    details varchar(2000),
    created_at timestamptz not null
);
create index ix_game_admin_audits_game_id on game_admin_audits(game_id, created_at);
create function reject_game_admin_audit_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'Game admin audit is append-only';
end;
$$;
create trigger game_admin_audit_immutable before update or delete on game_admin_audits
for each row execute function reject_game_admin_audit_mutation();
