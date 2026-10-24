-- Harici sa?lay?c? kimlikleri; Steam k?s?t? ba?lang??tan itibaren nihai bi?imdedir.
create table game_external_refs (
    id uuid primary key,
    game_id uuid not null references games(id) on delete restrict,
    provider varchar(32) not null check (provider ~ '^[A-Z][A-Z0-9_]*$'),
    external_id varchar(128) not null check (length(trim(external_id)) > 0),
    last_synced_at timestamptz,
    version bigint not null default 0,
    constraint uq_game_external_refs_provider_external_id unique(provider, external_id),
    constraint ck_game_external_refs_steam_appid check (
    provider <> 'STEAM' or case when external_id ~ '^[1-9][0-9]{0,9}$'
        then external_id::bigint <= 4294967295 else false end
)
);
create index ix_game_external_refs_game_id on game_external_refs(game_id);
