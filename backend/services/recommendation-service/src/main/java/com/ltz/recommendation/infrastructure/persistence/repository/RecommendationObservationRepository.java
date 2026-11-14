package com.ltz.recommendation.infrastructure.persistence.repository;

import com.ltz.recommendation.domain.entity.RecommendationObservation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationObservationRepository extends JpaRepository<RecommendationObservation, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO recommendation_observations
                (id, algorithm_version, occurred_at, outcome, candidate_count, result_count, total_score)
            VALUES (:id, :algorithm, :occurred, :outcome, :candidates, :results, :score)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insert(@Param("id") UUID id, @Param("algorithm") String algorithm,
            @Param("occurred") Instant occurred, @Param("outcome") String outcome,
            @Param("candidates") int candidates, @Param("results") int results, @Param("score") int score);

    @Query(value = """
            SELECT count(*) AS executions, coalesce(sum(result_count), 0) AS results,
                   coalesce(sum(total_score), 0) AS score,
                   count(*) FILTER (WHERE outcome = 'EMPTY') AS empty,
                   count(*) FILTER (WHERE outcome = 'UNAVAILABLE') AS unavailable
            FROM recommendation_observations WHERE occurred_at >= :since AND occurred_at < :until
            """, nativeQuery = true)
    Totals totals(@Param("since") Instant since, @Param("until") Instant until);

    @Modifying
    @Query(value = """
            DELETE FROM recommendation_observations WHERE id IN
                (SELECT id FROM recommendation_observations WHERE occurred_at < :before
                 ORDER BY occurred_at LIMIT 1000)
            """, nativeQuery = true)
    int deleteExpiredBatch(@Param("before") Instant before);

    interface Totals {
        long getExecutions();
        long getResults();
        long getScore();
        long getEmpty();
        long getUnavailable();
    }
}
