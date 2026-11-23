package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.domain.entity.MeasurementContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** En çok 21 oturum x 600 örnek; 21. satır yalnız bounded pencere tanısı içindir. */
@Repository
public class PerformanceEvidenceRepository {
    private final JdbcTemplate jdbc;
    public PerformanceEvidenceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Distribution(long rawCount, long inlierCount, Double p10, Double p90, Instant firstAt, Instant lastAt) { }

    public List<Distribution> distributions(UUID owner, UUID game, UUID profile, MeasurementContext context, Instant from, Instant to) {
        return jdbc.query("""
                WITH chosen AS (
                    SELECT id,device_link_id,started_at,ended_at FROM performance_sessions
                    WHERE user_id=? AND game_id=? AND hardware_profile_id=? AND hardware_profile_version=?
                      AND measurement_resolution=? AND measurement_graphics_preset=? AND measurement_rendering_mode=?
                      AND measurement_context_id=id AND status='COMPLETED' AND ended_at BETWEEN ? AND ?
                    ORDER BY ended_at DESC,id DESC LIMIT 21
                )
                SELECT d.* FROM chosen s CROSS JOIN LATERAL (
                    WITH raw AS MATERIALIZED (
                        SELECT value,captured_at FROM performance_samples
                        WHERE session_id=s.id AND measurement_context_id=s.id AND user_id=?
                          AND device_link_id=s.device_link_id AND source='DEVICE' AND metric='FPS'
                          AND captured_at BETWEEN GREATEST(s.started_at,?::timestamptz) AND LEAST(s.ended_at,?::timestamptz)
                        ORDER BY captured_at DESC LIMIT 600
                    ), bounds AS (
                        SELECT percentile_cont(0.25) WITHIN GROUP (ORDER BY value) q1,
                               percentile_cont(0.75) WITHIN GROUP (ORDER BY value) q3 FROM raw
                    ), clean AS (
                        SELECT r.* FROM raw r CROSS JOIN bounds b
                        WHERE r.value BETWEEN b.q1-1.5*(b.q3-b.q1) AND b.q3+1.5*(b.q3-b.q1)
                    )
                    SELECT (SELECT count(*) FROM raw) raw_count,count(*) inlier_count,
                           percentile_cont(0.10) WITHIN GROUP (ORDER BY value) p10,
                           percentile_cont(0.90) WITHIN GROUP (ORDER BY value) p90,
                           min(captured_at) first_at,max(captured_at) last_at FROM clean
                ) d ORDER BY s.ended_at DESC,s.id DESC
                """, (row, _) -> new Distribution(row.getLong("raw_count"), row.getLong("inlier_count"),
                        row.getObject("p10", Double.class), row.getObject("p90", Double.class),
                        row.getTimestamp("first_at") == null ? null : row.getTimestamp("first_at").toInstant(),
                        row.getTimestamp("last_at") == null ? null : row.getTimestamp("last_at").toInstant()),
                owner, game, profile, context.hardwareProfileVersion(), context.resolution(), context.graphicsPreset(),
                context.renderingMode(), Timestamp.from(from), Timestamp.from(to), owner, Timestamp.from(from), Timestamp.from(to));
    }
}
