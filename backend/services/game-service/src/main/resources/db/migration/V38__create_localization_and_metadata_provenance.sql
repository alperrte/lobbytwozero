-- GAME-23: yalnız eklemeli değişiklik; eski genel dil listesi ve veriler korunur.
create table game_language_supports (
    game_id uuid not null references games(id) on delete cascade,
    language_code varchar(35) not null,
    interface_support varchar(16) not null check(interface_support in ('UNKNOWN','SUPPORTED','UNSUPPORTED')),
    audio_support varchar(16) not null check(audio_support in ('UNKNOWN','SUPPORTED','UNSUPPORTED')),
    subtitle_support varchar(16) not null check(subtitle_support in ('UNKNOWN','SUPPORTED','UNSUPPORTED')),
    primary key(game_id,language_code)
);
create table game_metadata_provenance (
    game_id uuid not null references games(id) on delete cascade,
    field varchar(32) not null,
    source varchar(32) not null,
    data_state varchar(16) not null check(data_state in ('UNKNOWN','EMPTY','INVALID','AVAILABLE')),
    recorded_at timestamptz not null,
    verified_at timestamptz,
    primary key(game_id,field),
    check(verified_at is null or verified_at <= recorded_at)
);
