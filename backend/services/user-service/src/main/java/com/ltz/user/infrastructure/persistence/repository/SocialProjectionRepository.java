package com.ltz.user.infrastructure.persistence.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Social yansıması ({@code social_friendships}, {@code social_blocks}). Olaylar en az bir kez ve sırası bozuk
 * gelebileceği için her yazma "son durum" upsert'üdür: satır yalnız daha yeni {@code changed_at} ile güncellenir; kaldırılan
 * ilişki {@code active=false} olarak kalır ki eski bir "eklendi" olayı onu geri getiremesin.
 */
@Repository
@RequiredArgsConstructor
public class SocialProjectionRepository {

    private static final String RELATION_SOURCE = "a";
    private static final String RELATION_TARGET = "b";

    private final NamedParameterJdbcTemplate jdbc;

    public void upsertFriendship(UUID low, UUID high, boolean active, Instant changedAt) {
        jdbc.update("""
                INSERT INTO social_friendships (user_low_id, user_high_id, active, changed_at)
                VALUES (:low, :high, :active, :changedAt)
                ON CONFLICT (user_low_id, user_high_id) DO UPDATE
                    SET active = EXCLUDED.active, changed_at = EXCLUDED.changed_at
                    WHERE social_friendships.changed_at < EXCLUDED.changed_at""",
                new MapSqlParameterSource("low", low).addValue("high", high).addValue("active", active)
                        .addValue("changedAt", Timestamp.from(changedAt)));
    }

    public void upsertBlock(UUID blocker, UUID blocked, boolean active, Instant changedAt) {
        jdbc.update("""
                INSERT INTO social_blocks (blocker_id, blocked_id, active, changed_at)
                VALUES (:blocker, :blocked, :active, :changedAt)
                ON CONFLICT (blocker_id, blocked_id) DO UPDATE
                    SET active = EXCLUDED.active, changed_at = EXCLUDED.changed_at
                    WHERE social_blocks.changed_at < EXCLUDED.changed_at""",
                new MapSqlParameterSource("blocker", blocker).addValue("blocked", blocked).addValue("active", active)
                        .addValue("changedAt", Timestamp.from(changedAt)));
    }

    public boolean areFriends(UUID first, UUID second) {
        return !friendsAmong(first, List.of(second)).isEmpty();
    }

    /** {@code viewer}'ın arkadaşı olan {@code others} (tek sorgu). */
    public List<UUID> friendsAmong(UUID viewer, Collection<UUID> others) {
        return jdbc.queryForList("""
                SELECT CASE WHEN user_low_id = :a THEN user_high_id ELSE user_low_id END
                FROM social_friendships
                WHERE active AND ((user_low_id = :a AND user_high_id IN (:b)) OR (user_high_id = :a AND user_low_id IN (:b)))""",
                relation(viewer, others), UUID.class);
    }

    public boolean blockedEitherWay(UUID first, UUID second) {
        return !blockedAmong(first, List.of(second)).isEmpty();
    }

    /** {@code viewer} ile herhangi bir yönde engel ilişkisi olan {@code others} (tek sorgu). */
    public List<UUID> blockedAmong(UUID viewer, Collection<UUID> others) {
        return jdbc.queryForList("""
                SELECT CASE WHEN blocker_id = :a THEN blocked_id ELSE blocker_id END
                FROM social_blocks
                WHERE active AND ((blocker_id = :a AND blocked_id IN (:b)) OR (blocked_id = :a AND blocker_id IN (:b)))""",
                relation(viewer, others), UUID.class);
    }

    public long countFriends(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM social_friendships WHERE active AND (user_low_id = :a OR user_high_id = :a)",
                new MapSqlParameterSource(RELATION_SOURCE, userId), Long.class);
        return count == null ? 0 : count;
    }

    /** Hesap silme: hesabın taraf olduğu tüm yansıma satırları (aktif ya da değil). */
    public int deleteAllOf(UUID accountId) {
        MapSqlParameterSource params = new MapSqlParameterSource(RELATION_SOURCE, accountId);
        return jdbc.update("DELETE FROM social_friendships WHERE user_low_id = :a OR user_high_id = :a", params)
                + jdbc.update("DELETE FROM social_blocks WHERE blocker_id = :a OR blocked_id = :a", params);
    }

    private static MapSqlParameterSource relation(UUID viewer, Collection<UUID> others) {
        return new MapSqlParameterSource(RELATION_SOURCE, viewer).addValue(RELATION_TARGET, others);
    }
}
