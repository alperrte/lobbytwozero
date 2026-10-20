alter table game_provider_sync_state
    add column failure_count integer not null default 0 check (failure_count >= 0),
    add column next_attempt_at timestamptz;

alter table games
    add column description varchar(16000),
    add column release_date date,
    add column details_fetched_at timestamptz,
    add column details_expires_at timestamptz,
    add column details_next_attempt_at timestamptz,
    add column details_failure_count integer not null default 0 check (details_failure_count >= 0),
    add column details_last_error_code varchar(64);
