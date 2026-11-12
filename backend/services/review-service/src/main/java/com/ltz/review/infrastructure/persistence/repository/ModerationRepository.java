package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.api.dto.response.AdminReportResponse;
import com.ltz.review.api.dto.response.AdminReportedReviewResponse;
import com.ltz.review.api.dto.response.AdminReviewResponse;
import com.ltz.review.domain.enums.ReportReason;
import com.ltz.review.domain.enums.ReportStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Moderatör görünümü sorguları, rapor kapatma ve denetim kaydı. Raportör ve yazar kimliği hiçbir sorgu sonucunda dönmez. */
@Repository
public class ModerationRepository {
    private static final String STATUS = "status";
    private static final String REVIEW = "review";
    private static final String REASON = "reason";
    private final JdbcClient jdbc;

    public ModerationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public long countReportedReviews(ReportStatus status) {
        return Objects.requireNonNull(jdbc.sql("SELECT count(DISTINCT review_id) FROM review_reports WHERE status = :status")
                .param(STATUS, status.name()).query(Long.class).single());
    }

    /** Verilen durumdaki raporu olan incelemeler: en çok rapor alan, sonra en yeni rapor, sonra kimlik. */
    public List<AdminReportedReviewResponse> reportedReviews(ReportStatus status, int size, long offset) {
        return jdbc.sql("""
                SELECT r.id, r.game_id, r.status, r.body, count(*) AS report_count, max(p.created_at) AS latest,
                       (SELECT count(*) FROM review_reports t WHERE t.review_id = r.id) AS total_count
                FROM review_reports p JOIN reviews r ON r.id = p.review_id
                WHERE p.status = :status
                GROUP BY r.id
                ORDER BY report_count DESC, latest DESC, r.id ASC LIMIT :size OFFSET :offset
                """).param(STATUS, status.name()).param("size", size).param("offset", offset)
                .query((rs, row) -> new AdminReportedReviewResponse(rs.getObject("id", UUID.class), rs.getObject("game_id", UUID.class),
                        rs.getString(STATUS), rs.getString("body"), rs.getLong("report_count"), rs.getLong("total_count"),
                        rs.getTimestamp("latest").toInstant())).list();
    }

    public List<AdminReportResponse> reportsOf(UUID reviewId) {
        return jdbc.sql("""
                SELECT id, reason, detail, status, created_at, closed_at FROM review_reports
                WHERE review_id = :review ORDER BY created_at DESC, id ASC
                """).param(REVIEW, reviewId)
                .query((rs, row) -> new AdminReportResponse(rs.getObject("id", UUID.class), ReportReason.valueOf(rs.getString(REASON)),
                        rs.getString("detail"), rs.getString(STATUS), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant())).list();
    }

    public List<AdminReviewResponse.Entry> history(UUID reviewId) {
        return jdbc.sql("SELECT action, reason, review_version, created_at FROM review_moderation_actions WHERE review_id = :review ORDER BY created_at, id")
                .param(REVIEW, reviewId)
                .query((rs, row) -> new AdminReviewResponse.Entry(rs.getString("action"), rs.getString(REASON),
                        rs.getLong("review_version"), rs.getTimestamp("created_at").toInstant())).list();
    }

    public void addAction(UUID reviewId, UUID actor, String action, String reason, long reviewVersion, Instant now) {
        jdbc.sql("""
                INSERT INTO review_moderation_actions (id, review_id, actor_id, action, reason, review_version, created_at)
                VALUES (:id, :review, :actor, :action, :reason, :version, :now)
                """).param("id", UUID.randomUUID()).param(REVIEW, reviewId).param("actor", actor).param("action", action)
                .param(REASON, reason).param("version", reviewVersion).param("now", Timestamp.from(now)).update();
    }

    /** Açık (RECEIVED) raporları kapatır; kapatılan sayıyı döner. Kapanan rapor yeniden açılmaz. */
    public int closeOpen(UUID reviewId, ReportStatus outcome, UUID actor, Instant now) {
        return jdbc.sql("""
                UPDATE review_reports SET status = :outcome, closed_at = :now, closed_by = :actor, updated_at = :now, version = version + 1
                WHERE review_id = :review AND status = 'RECEIVED'
                """).param("outcome", outcome.name()).param("now", Timestamp.from(now)).param("actor", actor).param(REVIEW, reviewId).update();
    }

    /** Moderatör hesabı silinince denetim kaydı ve kapatma izi anonimleşir (kayıt ve gerekçe kalır). */
    public void anonymizeActor(UUID actor) {
        jdbc.sql("UPDATE review_moderation_actions SET actor_id = NULL WHERE actor_id = ?").param(actor).update();
        jdbc.sql("UPDATE review_reports SET closed_by = NULL WHERE closed_by = ?").param(actor).update();
    }
}
