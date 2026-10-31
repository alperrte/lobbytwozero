package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.api.dto.response.RatingResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RatingRepository {
    private final JdbcClient jdbc;

    public RatingRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void put(UUID userId, UUID gameId, int rating) {
        jdbc.sql("""
                INSERT INTO game_ratings (id, user_id, game_id, rating, created_at, updated_at)
                VALUES (:id, :user, :game, :rating, clock_timestamp(), clock_timestamp())
                ON CONFLICT (user_id, game_id) DO UPDATE
                SET rating = EXCLUDED.rating, version = game_ratings.version + 1, updated_at = clock_timestamp()
                WHERE game_ratings.rating <> EXCLUDED.rating
                """).param("id", UUID.randomUUID()).param("user", userId).param("game", gameId).param("rating", rating).update();
    }

    public Optional<RatingResponse> find(UUID userId, UUID gameId) {
        return jdbc.sql("SELECT game_id, rating, version, created_at, updated_at FROM game_ratings WHERE user_id = :user AND game_id = :game")
                .param("user", userId).param("game", gameId)
                .query((rs, _) -> new RatingResponse(rs.getObject("game_id", UUID.class), rs.getInt("rating"), rs.getLong("version"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant())).optional();
    }
}
