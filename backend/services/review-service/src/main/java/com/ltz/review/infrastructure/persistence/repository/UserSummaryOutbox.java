package com.ltz.review.infrastructure.persistence.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Kullanıcı başına birleştirilmiş outbox (V59). Yazma, inceleme değişikliğiyle AYNI transaction'dadır. */
@Repository
public class UserSummaryOutbox {
    /** Gönderilecek bir olay: aynı {@code eventId} yeniden denemelerde sabittir. */
    public record Pending(UUID userId, UUID eventId, Instant occurredAt, int attempts) {}

    private static final String EVENT = "event";
    private static final String USER = "user";
    private final JdbcClient jdbc;

    public UserSummaryOutbox(JdbcClient jdbc) { this.jdbc = jdbc; }

    /**
     * Kullanıcının inceleme sayısı değişti: bekleyen kaydı yeni olay kimliğiyle yeniler. {@code occurred_at} kullanıcı başına
     * kesin artar (en az 1 ms), böylece User'ın "eşit/eski zamanı yok say" kuralı ikinci değişikliği kaybetmez.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void touch(UUID userId, Instant now) {
        jdbc.sql("""
                INSERT INTO review_user_summary_outbox (user_id, event_id, occurred_at, next_attempt_at)
                VALUES (:user, :event, :now, :now)
                ON CONFLICT (user_id) DO UPDATE SET
                    event_id = EXCLUDED.event_id,
                    occurred_at = greatest(EXCLUDED.occurred_at, review_user_summary_outbox.occurred_at + interval '1 millisecond'),
                    attempts = 0, next_attempt_at = EXCLUDED.next_attempt_at, delivered_at = NULL, last_error = NULL
                """)
                .param(USER, userId).param(EVENT, UUID.randomUUID()).param("now", Timestamp.from(now)).update();
    }

    /** Hesap silme: silinen hesabın hiçbir olay izi kalmaz ve silinmiş hesap için yeni olay üretilmez. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void remove(UUID userId) {
        jdbc.sql("DELETE FROM review_user_summary_outbox WHERE user_id = ?").param(userId).update();
    }

    public List<Pending> due(Instant now, int limit) {
        return jdbc.sql("""
                SELECT user_id, event_id, occurred_at, attempts FROM review_user_summary_outbox
                WHERE delivered_at IS NULL AND next_attempt_at <= :now ORDER BY occurred_at, user_id LIMIT :limit
                """).param("now", Timestamp.from(now)).param("limit", limit)
                .query((rs, row) -> new Pending(rs.getObject("user_id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getInt("attempts"))).list();
    }

    /** Gönderim anındaki görünür inceleme sayısı (yalnız VISIBLE; silinen sayılmaz). */
    public int visibleCount(UUID userId) {
        return Objects.requireNonNull(jdbc.sql("SELECT count(*) FROM reviews WHERE user_id = ? AND status = 'VISIBLE'").param(userId)
                .query(Integer.class).single());
    }

    /** Yalnız olay hâlâ aynıysa teslim edildi işaretler; gönderim sırasında yeni değişiklik olduysa kayıt bekler. */
    public boolean markDelivered(UUID userId, UUID eventId, Instant now) {
        return jdbc.sql("UPDATE review_user_summary_outbox SET delivered_at = :now, last_error = NULL WHERE user_id = :user AND event_id = :event AND delivered_at IS NULL")
                .param("now", Timestamp.from(now)).param(USER, userId).param(EVENT, eventId).update() == 1;
    }

    public void markFailed(UUID userId, UUID eventId, String errorCode, Instant nextAttempt) {
        jdbc.sql("UPDATE review_user_summary_outbox SET attempts = attempts + 1, last_error = :error, next_attempt_at = :next WHERE user_id = :user AND event_id = :event AND delivered_at IS NULL")
                .param("error", errorCode).param("next", Timestamp.from(nextAttempt)).param(USER, userId).param(EVENT, eventId).update();
    }
}
