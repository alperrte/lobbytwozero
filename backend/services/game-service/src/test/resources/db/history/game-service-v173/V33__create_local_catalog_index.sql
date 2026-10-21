alter table games alter column normalized_title type varchar(1024);
create index ix_games_normalized_title on games(normalized_title varchar_pattern_ops, id);
create table game_provider_sync_state (
    id uuid primary key,
    provider varchar(32) not null unique,
    cursor bigint not null default 0 check(cursor >= 0),
    run_mode varchar(32) check(run_mode in ('INITIAL','INCREMENTAL')),
    sync_status varchar(32) not null default 'IDLE' check(sync_status in ('IDLE','RUNNING','COMPLETE','FAILED')),
    query_since timestamptz,
    run_started_at timestamptz,
    last_successful_at timestamptz,
    processed_count bigint not null default 0 check(processed_count >= 0),
    last_error_code varchar(64),
    version bigint not null default 0
);
insert into game_provider_sync_state(id, provider) values ('00000000-0000-0000-0000-000000000001', 'STEAM');
