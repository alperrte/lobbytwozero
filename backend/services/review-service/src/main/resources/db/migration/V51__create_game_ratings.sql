CREATE TABLE game_ratings (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    game_id uuid NOT NULL,
    rating smallint NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_game_ratings_user_game UNIQUE (user_id, game_id),
    CONSTRAINT ck_game_ratings_rating CHECK (rating BETWEEN 1 AND 10),
    CONSTRAINT ck_game_ratings_version CHECK (version >= 0),
    CONSTRAINT ck_game_ratings_times CHECK (updated_at >= created_at)
);
CREATE INDEX ix_game_ratings_game_id ON game_ratings (game_id);
