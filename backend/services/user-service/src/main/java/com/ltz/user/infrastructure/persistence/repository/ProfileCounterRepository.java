package com.ltz.user.infrastructure.persistence.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Profil özeti sayaçları (USER-12). Kaynaklar (library, review) <b>mutlak</b> sayı gönderir; her kaynak kendi zaman
 * damgasıyla yazılır ve eski bir olay yenisini ezemez. Sütunun {@code NULL} olması "kaynaktan henüz veri gelmedi" demektir.
 */
@Repository
@RequiredArgsConstructor
public class ProfileCounterRepository {

    private static final String USER = "userId";
    private static final String CHANGED_AT = "changedAt";

    private final NamedParameterJdbcTemplate jdbc;

    /** Okunan sayaçlar; kaynak verisi yoksa ilgili alan {@code null}. */
    public record Counters(Integer libraryCollection, Integer libraryCompleted, Integer libraryFavorite,
                           Integer reviews) {
    }

    public void upsertLibrary(UUID userId, int collection, int completed, int favorite, Instant changedAt) {
        jdbc.update("""
                INSERT INTO profile_counters (user_id, library_collection_count, library_completed_count,
                                              library_favorite_count, library_changed_at)
                VALUES (:userId, :collection, :completed, :favorite, :changedAt)
                ON CONFLICT (user_id) DO UPDATE
                    SET library_collection_count = EXCLUDED.library_collection_count,
                        library_completed_count = EXCLUDED.library_completed_count,
                        library_favorite_count = EXCLUDED.library_favorite_count,
                        library_changed_at = EXCLUDED.library_changed_at
                    WHERE profile_counters.library_changed_at IS NULL
                       OR profile_counters.library_changed_at < EXCLUDED.library_changed_at""",
                new MapSqlParameterSource(USER, userId).addValue("collection", collection)
                        .addValue("completed", completed).addValue("favorite", favorite)
                        .addValue(CHANGED_AT, Timestamp.from(changedAt)));
    }

    public void upsertReviews(UUID userId, int reviews, Instant changedAt) {
        jdbc.update("""
                INSERT INTO profile_counters (user_id, review_count, review_changed_at)
                VALUES (:userId, :reviews, :changedAt)
                ON CONFLICT (user_id) DO UPDATE
                    SET review_count = EXCLUDED.review_count, review_changed_at = EXCLUDED.review_changed_at
                    WHERE profile_counters.review_changed_at IS NULL
                       OR profile_counters.review_changed_at < EXCLUDED.review_changed_at""",
                new MapSqlParameterSource(USER, userId).addValue("reviews", reviews)
                        .addValue(CHANGED_AT, Timestamp.from(changedAt)));
    }

    public Optional<Counters> find(UUID userId) {
        List<Counters> rows = jdbc.query("""
                SELECT library_collection_count, library_completed_count, library_favorite_count, review_count
                FROM profile_counters WHERE user_id = :userId""",
                new MapSqlParameterSource(USER, userId),
                (row, index) -> new Counters((Integer) row.getObject(1), (Integer) row.getObject(2),
                        (Integer) row.getObject(3), (Integer) row.getObject(4)));
        return rows.stream().findFirst();
    }

    public int deleteFor(UUID userId) {
        return jdbc.update("DELETE FROM profile_counters WHERE user_id = :userId",
                new MapSqlParameterSource(USER, userId));
    }
}
