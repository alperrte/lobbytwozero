alter table games add column game_type varchar(32) not null default 'UNKNOWN'
    check(game_type in ('MAIN_GAME','DLC','DEMO','SOUNDTRACK','SOFTWARE','VIDEO','PACKAGE','HARDWARE','UNKNOWN'));
create index ix_games_active_type_title on games(game_type, normalized_title varchar_pattern_ops, id) where status='ACTIVE';
