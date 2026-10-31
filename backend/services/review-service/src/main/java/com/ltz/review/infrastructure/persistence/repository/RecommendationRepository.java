package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.api.dto.response.RecommendationResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationRepository {
    private final JdbcClient jdbc;

    public RecommendationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void put(UUID userId, UUID gameId, boolean recommended) {
        jdbc.sql("""
                INSERT INTO game_recommendations (id, user_id, game_id, recommended, created_at, updated_at)
                VALUES (:id, :user, :game, :recommended, clock_timestamp(), clock_timestamp())
                ON CONFLICT (user_id, game_id) DO UPDATE
                SET recommended = EXCLUDED.recommended, version = game_recommendations.version + 1, updated_at = clock_timestamp()
                WHERE game_recommendations.recommended <> EXCLUDED.recommended
                """).param("id", UUID.randomUUID()).param("user", userId).param("game", gameId).param("recommended", recommended).update();
    }

    public Optional<RecommendationResponse> find(UUID userId, UUID gameId) {
        return jdbc.sql("SELECT game_id, recommended, version, created_at, updated_at FROM game_recommendations WHERE user_id = :user AND game_id = :game")
                .param("user", userId).param("game", gameId)
                .query((rs, _) -> new RecommendationResponse(rs.getObject("game_id", UUID.class), rs.getBoolean("recommended"), rs.getLong("version"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant())).optional();
    }
}
