CREATE INDEX ix_reviews_visible_game_created_id
    ON reviews (game_id, created_at DESC, id ASC)
    WHERE status = 'VISIBLE';
