package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.api.dto.response.ReviewLikeResponse;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewLikeRepository {
    private final JdbcClient jdbc;

    public ReviewLikeRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void add(UUID reviewId, UUID actor) {
        jdbc.sql("INSERT INTO review_likes (review_id,user_id,created_at) VALUES (?,?,clock_timestamp()) ON CONFLICT (review_id,user_id) DO NOTHING")
                .params(reviewId, actor).update();
    }

    public void remove(UUID reviewId, UUID actor) {
        jdbc.sql("DELETE FROM review_likes WHERE review_id=? AND user_id=?").params(reviewId, actor).update();
    }

    public void removeForReview(UUID reviewId) {
        jdbc.sql("DELETE FROM review_likes WHERE review_id=?").param(reviewId).update();
    }

    public ReviewLikeResponse state(UUID reviewId, UUID actor) {
        return jdbc.sql("SELECT count(*) AS like_count, coalesce(bool_or(user_id=?),false) AS liked FROM review_likes WHERE review_id=?")
                .params(actor, reviewId).query((rs, row) -> new ReviewLikeResponse(reviewId, rs.getLong("like_count"), rs.getBoolean("liked"))).single();
    }
}
