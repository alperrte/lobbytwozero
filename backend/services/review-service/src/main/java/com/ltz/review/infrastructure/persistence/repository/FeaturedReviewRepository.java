package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.api.dto.response.FeaturedReviewResponse;
import com.ltz.review.api.dto.response.ReviewResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FeaturedReviewRepository {
    // Visibility and available ranking signals are centralized here; reports/moderation are not yet available.
    private static final String RANKED = """
            WITH ranked AS (
                SELECT r.id, r.game_id, r.user_id, r.body, r.version, r.created_at, r.updated_at,
                       count(l.user_id) AS like_count
                FROM reviews r LEFT JOIN review_likes l ON l.review_id = r.id
                WHERE r.game_id = :game AND r.status = 'VISIBLE'
                GROUP BY r.id
            )
            """;
    private final JdbcClient jdbc;

    public FeaturedReviewRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public long count(UUID game, int minLikes) {
        return jdbc.sql(RANKED + "SELECT count(*) FROM ranked WHERE like_count >= :minLikes")
                .param("game", game).param("minLikes", minLikes).query((rs, row) -> rs.getLong(1)).single();
    }

    public List<FeaturedReviewResponse> find(UUID game, UUID actor, int minLikes, int size, long offset) {
        return jdbc.sql(RANKED + """
                SELECT * FROM ranked WHERE like_count >= :minLikes
                ORDER BY like_count DESC, created_at DESC, id ASC LIMIT :size OFFSET :offset
                """)
                .param("game", game).param("minLikes", minLikes).param("size", size).param("offset", offset)
                .query((rs, row) -> new FeaturedReviewResponse(new ReviewResponse(
                        rs.getObject("id", UUID.class), rs.getObject("game_id", UUID.class), rs.getString("body"),
                        actor.equals(rs.getObject("user_id", UUID.class)), rs.getLong("version"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                        rs.getLong("like_count"))).list();
    }
}
