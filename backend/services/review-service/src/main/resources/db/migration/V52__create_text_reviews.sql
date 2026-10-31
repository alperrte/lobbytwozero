CREATE TABLE reviews (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    game_id uuid NOT NULL,
    body varchar(5000),
    status varchar(32) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT ck_reviews_status CHECK (status IN ('VISIBLE', 'DELETED')),
    CONSTRAINT ck_reviews_content CHECK (
        (status = 'VISIBLE' AND body IS NOT NULL AND body ~ '[^[:space:]]' AND deleted_at IS NULL)
        OR (status = 'DELETED' AND body IS NULL AND deleted_at IS NOT NULL)
    ),
    CONSTRAINT ck_reviews_version CHECK (version >= 0),
    CONSTRAINT ck_reviews_times CHECK (updated_at >= created_at AND (deleted_at IS NULL OR deleted_at >= created_at))
);
CREATE UNIQUE INDEX uq_reviews_active_user_game ON reviews (user_id, game_id) WHERE status <> 'DELETED';
CREATE INDEX ix_reviews_game_status ON reviews (game_id, status, created_at, id);
CREATE INDEX ix_reviews_user_id ON reviews (user_id);
