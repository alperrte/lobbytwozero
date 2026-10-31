CREATE TABLE library_entries (
    id uuid CONSTRAINT pk_library_entries PRIMARY KEY,
    user_id uuid NOT NULL,
    game_id uuid NOT NULL,
    in_collection boolean NOT NULL DEFAULT true,
    favorite boolean NOT NULL DEFAULT false,
    completed boolean NOT NULL DEFAULT false,
    added_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_library_entries_user_game UNIQUE (user_id, game_id),
    CONSTRAINT ck_library_entries_collection CHECK (in_collection)
);
