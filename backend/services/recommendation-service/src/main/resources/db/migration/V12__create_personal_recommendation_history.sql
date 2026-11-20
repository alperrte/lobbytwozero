CREATE TABLE recommendation_runs (
    id uuid CONSTRAINT pk_recommendation_runs PRIMARY KEY,
    user_id uuid NOT NULL,
    algorithm_version varchar(32) NOT NULL,
    genre varchar(64),
    platform varchar(64),
    result_limit integer NOT NULL CHECK (result_limit BETWEEN 1 AND 50),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_recommendation_runs_user_created ON recommendation_runs (user_id, created_at DESC, id DESC);
CREATE INDEX ix_recommendation_runs_expires ON recommendation_runs (expires_at);
CREATE TABLE recommendation_items (
    id uuid CONSTRAINT pk_recommendation_items PRIMARY KEY,
    run_id uuid NOT NULL CONSTRAINT fk_recommendation_items_run REFERENCES recommendation_runs(id) ON DELETE CASCADE,
    game_id uuid NOT NULL,
    score integer NOT NULL CHECK (score BETWEEN 1 AND 10000),
    rank integer NOT NULL CHECK (rank BETWEEN 1 AND 50),
    matched_genres varchar(64)[] NOT NULL CHECK (cardinality(matched_genres) BETWEEN 1 AND 10),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_recommendation_items_run_rank UNIQUE (run_id, rank),
    CONSTRAINT uq_recommendation_items_run_game UNIQUE (run_id, game_id)
);
CREATE TABLE recommendation_feedback (
    id uuid CONSTRAINT pk_recommendation_feedback PRIMARY KEY,
    item_id uuid NOT NULL CONSTRAINT fk_recommendation_feedback_item REFERENCES recommendation_items(id) ON DELETE CASCADE,
    feedback varchar(32) NOT NULL CHECK (feedback IN ('LIKE', 'NOT_INTERESTED')),
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL CHECK (version >= 1),
    CONSTRAINT uq_recommendation_feedback_item UNIQUE (item_id)
);
