-- GAME-24: ITAD fiyatları yalnız belirtilen ülke ve para birimi için geçerlidir.
create table game_price_quotes (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    country varchar(2) not null check(country ~ '^[A-Z]{2}$'),
    currency varchar(3) not null check(currency ~ '^[A-Z]{3}$'),
    store varchar(64) not null,
    regular_price numeric(19,4) not null check(regular_price >= 0),
    current_price numeric(19,4) not null check(current_price >= 0 and current_price <= regular_price),
    discount_percent smallint not null check(discount_percent between 0 and 100),
    source varchar(16) not null check(source in ('ITAD','STEAM')),
    url varchar(2048) not null,
    source_updated_at timestamptz not null,
    fetched_at timestamptz not null,
    expires_at timestamptz not null check(expires_at > fetched_at),
    version bigint not null default 0,
    unique(game_id,country,currency,store)
);
create index ix_game_price_discount on game_price_quotes(country,currency,game_id,expires_at) where current_price < regular_price;
create table game_price_refresh_state (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    country varchar(2) not null check(country ~ '^[A-Z]{2}$'),
    currency varchar(3) not null check(currency ~ '^[A-Z]{3}$'),
    status varchar(16) not null check(status in ('AVAILABLE','NO_OFFER','FAILED')),
    checked_at timestamptz not null,
    next_attempt_at timestamptz,
    error_code varchar(64),
    version bigint not null default 0,
    unique(game_id,country,currency)
);
create table game_price_sync_lock (
    id uuid primary key check(id = '00000000-0000-0000-0000-000000000003'),
    owner uuid,
    expires_at timestamptz,
    version bigint not null default 0,
    check((owner is null) = (expires_at is null))
);
insert into game_price_sync_lock(id) values ('00000000-0000-0000-0000-000000000003');
