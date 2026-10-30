-- Public Game kartının son kullanılabilir özeti; kullanıcı verisi veya dış DB FK'sı yoktur.
CREATE TABLE game_snapshots (
    game_id uuid PRIMARY KEY,
    title varchar(256) NOT NULL CHECK (length(trim(title)) > 0),
    cover_url varchar(2048),
    origin varchar(16) CHECK (origin IN ('INTERNAL', 'EXTERNAL')),
    metadata_completeness varchar(16) CHECK (metadata_completeness IN ('INDEX_ONLY', 'PARTIAL', 'FULL')),
    game_type varchar(32) CHECK (game_type IN ('MAIN_GAME', 'DLC', 'DEMO', 'SOUNDTRACK', 'SOFTWARE', 'VIDEO', 'PACKAGE', 'HARDWARE', 'UNKNOWN')),
    status varchar(16) NOT NULL DEFAULT 'AVAILABLE' CHECK (status = 'AVAILABLE'),
    refreshed_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
