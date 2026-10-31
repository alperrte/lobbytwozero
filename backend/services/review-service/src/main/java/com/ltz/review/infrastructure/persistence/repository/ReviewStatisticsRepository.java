package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.api.dto.response.GameReviewStatisticsResponse;
import com.ltz.review.api.dto.response.GameReviewStatisticsResponse.Bucket;
import com.ltz.review.api.dto.response.GameReviewStatisticsResponse.Ratings;
import com.ltz.review.api.dto.response.GameReviewStatisticsResponse.Recommendations;
import com.ltz.review.api.dto.response.GameReviewStatisticsResponse.Reviews;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewStatisticsRepository {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final JdbcClient jdbc;

    public ReviewStatisticsRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    /**
     * Tüm değerler tek SQL ifadesinde, tek snapshot'tan okunur. Ortalama REV-08/REV-12 ile aynı
     * {@code round(avg(rating),2)} ifadesidir; ikinci bir hesap politikası yoktur.
     */
    public GameReviewStatisticsResponse find(UUID gameId) {
        return jdbc.sql("""
                SELECT
                  (SELECT count(*) FROM game_ratings WHERE game_id = :game) AS rating_count,
                  (SELECT round(avg(rating),2) FROM game_ratings WHERE game_id = :game) AS average_rating,
                  (SELECT array_agg(coalesce(d.users, 0) ORDER BY s.rating)
                     FROM generate_series(1, 10) AS s(rating)
                     LEFT JOIN (SELECT rating, count(*) AS users FROM game_ratings WHERE game_id = :game GROUP BY rating) d
                       ON d.rating = s.rating)::bigint[] AS distribution,
                  (SELECT count(*) FROM reviews WHERE game_id = :game AND status = 'VISIBLE') AS active_reviews,
                  (SELECT count(*) FROM game_recommendations WHERE game_id = :game AND recommended) AS recommended,
                  (SELECT count(*) FROM game_recommendations WHERE game_id = :game AND NOT recommended) AS not_recommended
                """).param("game", gameId).query((rs, row) -> {
                    long recommended = rs.getLong("recommended");
                    long notRecommended = rs.getLong("not_recommended");
                    long total = recommended + notRecommended;
                    return new GameReviewStatisticsResponse(gameId,
                            new Ratings(rs.getLong("rating_count"), rs.getBigDecimal("average_rating"), buckets(rs.getArray("distribution"))),
                            new Reviews(rs.getLong("active_reviews")),
                            new Recommendations(recommended, notRecommended, total, percentage(recommended, total), percentage(notRecommended, total)));
                }).single();
    }

    static BigDecimal percentage(long part, long total) {
        if (total == 0) return null;
        return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static List<Bucket> buckets(java.sql.Array array) throws SQLException {
        Long[] counts = (Long[]) array.getArray();
        List<Bucket> result = new ArrayList<>(counts.length);
        for (int rating = 1; rating <= counts.length; rating++) result.add(new Bucket(rating, counts[rating - 1]));
        return List.copyOf(result);
    }
}
