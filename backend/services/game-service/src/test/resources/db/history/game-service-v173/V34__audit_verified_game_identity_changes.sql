alter table game_external_refs add constraint ck_game_external_refs_steam_appid check (
    provider <> 'STEAM' or case when external_id ~ '^[1-9][0-9]{0,9}$'
        then external_id::bigint <= 4294967295 else false end
);
create table game_identity_audits (
    id uuid primary key,
    reference_id uuid not null references game_external_refs(id) on delete restrict,
    from_game_id uuid not null references games(id) on delete restrict,
    to_game_id uuid not null references games(id) on delete restrict,
    actor_id uuid not null,
    action varchar(32) not null check(action in ('MAP','SPLIT')),
    evidence varchar(1000) not null check(length(trim(evidence)) >= 8),
    created_at timestamptz not null,
    check(from_game_id <> to_game_id)
);
create index ix_game_identity_audits_reference_id on game_identity_audits(reference_id);
create function reject_game_identity_audit_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'Game identity audit is append-only';
end;
$$;
create trigger game_identity_audit_immutable before update or delete on game_identity_audits
for each row execute function reject_game_identity_audit_mutation();
