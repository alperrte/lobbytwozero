package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.api.dto.response.ReviewReportResponse;
import com.ltz.review.domain.enums.ReportReason;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewReportRepository {
    private static final String COLUMNS = "id, review_id, reason, detail, status, created_at";
    private final JdbcClient jdbc;

    public ReviewReportRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    /** Aynı (inceleme, raportör) için ikinci satır eklenmez; eklenmediyse 0 döner. */
    public int insertIfAbsent(UUID id, UUID reviewId, UUID reporter, ReportReason reason, String detail, Instant now) {
        return jdbc.sql("""
                INSERT INTO review_reports (id, review_id, reporter_id, reason, detail, created_at, updated_at)
                VALUES (:id, :review, :reporter, :reason, :detail, :now, :now)
                ON CONFLICT (review_id, reporter_id) DO NOTHING
                """)
                .param("id", id).param("review", reviewId).param("reporter", reporter)
                .param("reason", reason.name()).param("detail", detail)
                .param("now", java.sql.Timestamp.from(now)).update();
    }

    public Optional<ReviewReportResponse> find(UUID reviewId, UUID reporter) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM review_reports WHERE review_id = :review AND reporter_id = :reporter")
                .param("review", reviewId).param("reporter", reporter)
                .query((rs, row) -> new ReviewReportResponse(rs.getObject("id", UUID.class), rs.getObject("review_id", UUID.class),
                        ReportReason.valueOf(rs.getString("reason")), rs.getString("detail"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }
}
