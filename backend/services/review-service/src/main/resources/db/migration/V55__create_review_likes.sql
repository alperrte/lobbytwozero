CREATE TABLE review_likes (
    review_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_review_likes PRIMARY KEY (review_id, user_id),
    CONSTRAINT fk_review_likes_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT ck_review_likes_version CHECK (version >= 0)
);
CREATE INDEX ix_review_likes_user_id ON review_likes (user_id);
