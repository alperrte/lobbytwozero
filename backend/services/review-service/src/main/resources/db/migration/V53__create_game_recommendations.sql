CREATE TABLE game_recommendations (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    game_id uuid NOT NULL,
    recommended boolean NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_game_recommendations_user_game UNIQUE (user_id, game_id),
    CONSTRAINT ck_game_recommendations_version CHECK (version >= 0),
    CONSTRAINT ck_game_recommendations_times CHECK (updated_at >= created_at)
);
CREATE INDEX ix_game_recommendations_game_id ON game_recommendations (game_id);
