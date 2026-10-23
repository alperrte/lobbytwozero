alter table games add column origin varchar(32) not null default 'INTERNAL'
    check (origin in ('INTERNAL','EXTERNAL'));
alter table games add column metadata_completeness varchar(32) not null default 'INDEX_ONLY'
    check (metadata_completeness in ('INDEX_ONLY','PARTIAL','FULL'));
alter table games add column status varchar(32) not null default 'ACTIVE'
    check (status in ('ACTIVE','ARCHIVED','MERGED'));
create table game_external_refs (
    id uuid primary key,
    game_id uuid not null references games(id) on delete restrict,
    provider varchar(32) not null check (provider ~ '^[A-Z][A-Z0-9_]*$'),
    external_id varchar(128) not null check (length(trim(external_id)) > 0),
    last_synced_at timestamptz,
    version bigint not null default 0,
    constraint uq_game_external_refs_provider_external_id unique(provider, external_id)
);
create index ix_game_external_refs_game_id on game_external_refs(game_id);
