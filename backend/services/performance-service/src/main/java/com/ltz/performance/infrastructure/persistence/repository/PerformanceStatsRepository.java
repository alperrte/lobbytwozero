package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.api.dto.response.MetricStatsResponse;
import com.ltz.performance.api.dto.response.SamplePointResponse;
import com.ltz.performance.api.dto.response.SeriesPointResponse;
import com.ltz.performance.application.service.SessionFilter;
import com.ltz.performance.application.service.TimeWindow;
import com.ltz.performance.domain.enums.SampleMetric;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Salt okunur özet/seri sorguları. Her sorgu sahiplik ({@code user_id}) koşulunu taşır; tüm değerler bağlı
 * parametredir, SQL parçaları sabittir. Örnek sorguları {@code uq_performance_samples_point
 * (session_id, metric, captured_at)} indeksinden yürür; sonuç boyutu çağıran tarafından sınırlanır.
 */
@Repository
public class PerformanceStatsRepository {
    private static final String STATS_COLUMNS = """
            SELECT p.metric, count(*), count(DISTINCT p.session_id), avg(p.value), min(p.value), max(p.value),
                   min(p.captured_at), max(p.captured_at)
            """;
    private static final String ORDER_BY_METRIC = " GROUP BY p.metric ORDER BY p.metric";

    private final JdbcTemplate jdbc;

    public PerformanceStatsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Seri için sayım ve gerçek zaman sınırları; örnek yoksa {@code count=0} ve sınırlar {@code null}. */
    public record Extent(long count, Instant first, Instant last) { }

    /** Tek oturumun metrik özeti; {@code metric} verilirse yalnız o metrik. Sıra: metrik adı. */
    public List<MetricStatsResponse> sessionStats(UUID sessionId, UUID userId, SampleMetric metric, TimeWindow window) {
        var args = new ArrayList<Object>(List.of(sessionId, userId));
        var sql = new StringBuilder(STATS_COLUMNS)
                .append("FROM performance_samples p WHERE p.session_id=? AND p.user_id=?");
        sampleConditions(sql, args, metric, window);
        sql.append(ORDER_BY_METRIC);
        return jdbc.query(sql.toString(), (row, _) -> stats(row), args.toArray());
    }

    /** Filtreye uyan tüm oturumların metrik özeti (örnek ağırlıklı). Sıra: metrik adı. */
    public List<MetricStatsResponse> accountStats(UUID userId, SessionFilter filter, SampleMetric metric) {
        var args = new ArrayList<Object>(List.of(userId, userId));
        var sql = new StringBuilder(STATS_COLUMNS).append("""
                FROM performance_samples p JOIN performance_sessions s ON s.id=p.session_id
                WHERE s.user_id=? AND p.user_id=?""");
        sessionConditions(sql, args, filter);
        if (metric != null) {
            sql.append(" AND p.metric=?");
            args.add(metric.name());
        }
        sql.append(ORDER_BY_METRIC);
        return jdbc.query(sql.toString(), (row, _) -> stats(row), args.toArray());
    }

    /** Filtreye uyan oturum sayısı (örneksiz oturumlar dahil). */
    public long countSessions(UUID userId, SessionFilter filter) {
        var args = new ArrayList<Object>(List.of(userId));
        var sql = new StringBuilder("SELECT count(*) FROM performance_sessions s WHERE s.user_id=?");
        sessionConditions(sql, args, filter);
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return Objects.requireNonNull(total, "session count");
    }

    /** Ham örnek sayfası; sıra (captured_at, metric, id) sabittir. Çağıran hasNext için {@code size + 1} ister. */
    public List<SamplePointResponse> samples(UUID sessionId, UUID userId, SampleMetric metric, TimeWindow window,
            int offset, int limit) {
        var args = new ArrayList<Object>(List.of(sessionId, userId));
        var sql = new StringBuilder("SELECT p.metric, p.captured_at, p.value FROM performance_samples p "
                + "WHERE p.session_id=? AND p.user_id=?");
        sampleConditions(sql, args, metric, window);
        sql.append(" ORDER BY p.captured_at, p.metric, p.id OFFSET ? LIMIT ?");
        args.add(offset);
        args.add(limit);
        return jdbc.query(sql.toString(), (row, _) -> {
            var parsed = SampleMetric.valueOf(row.getString(1));
            return new SamplePointResponse(parsed.name(), parsed.unit(), row.getTimestamp(2).toInstant(),
                    row.getDouble(3));
        }, args.toArray());
    }

    public Extent extent(UUID sessionId, UUID userId, SampleMetric metric, TimeWindow window) {
        var args = new ArrayList<Object>(List.of(sessionId, userId));
        var sql = new StringBuilder("SELECT count(*), min(p.captured_at), max(p.captured_at) "
                + "FROM performance_samples p WHERE p.session_id=? AND p.user_id=?");
        sampleConditions(sql, args, metric, window);
        return jdbc.queryForObject(sql.toString(), (row, _) -> new Extent(row.getLong(1),
                instant(row.getTimestamp(2)), instant(row.getTimestamp(3))), args.toArray());
    }

    /** Ham nokta okuma; üst sınır {@code last} sabitlenir ki sayım ile okuma arasında gelen örnek taşırmasın. */
    public List<SeriesPointResponse> rawPoints(UUID sessionId, UUID userId, SampleMetric metric, Extent extent,
            int limit) {
        return jdbc.query("""
                SELECT captured_at, value FROM performance_samples
                WHERE session_id=? AND user_id=? AND metric=? AND captured_at>=? AND captured_at<=?
                ORDER BY captured_at LIMIT ?
                """, (row, _) -> {
                    double value = row.getDouble(2);
                    return new SeriesPointResponse(row.getTimestamp(1).toInstant(), value, value, value, 1);
                }, sessionId, userId, metric.name(), Timestamp.from(extent.first()), Timestamp.from(extent.last()),
                limit);
    }

    /** Kova başına ortalama/min/max/sayı; kova başlangıcı {@code first + b * bucketMillis}'tir. */
    public List<SeriesPointResponse> bucketPoints(UUID sessionId, UUID userId, SampleMetric metric, Extent extent,
            long bucketMillis) {
        Instant first = extent.first();
        return jdbc.query("""
                SELECT floor(extract(epoch FROM (captured_at - ?::timestamptz)) * 1000 / ?)::bigint AS bucket,
                       avg(value), min(value), max(value), count(*)
                FROM performance_samples
                WHERE session_id=? AND user_id=? AND metric=? AND captured_at>=? AND captured_at<=?
                GROUP BY bucket ORDER BY bucket
                """, (row, _) -> new SeriesPointResponse(first.plusMillis(row.getLong(1) * bucketMillis),
                        row.getDouble(2), row.getDouble(3), row.getDouble(4), row.getLong(5)),
                Timestamp.from(first), bucketMillis, sessionId, userId, metric.name(), Timestamp.from(first),
                Timestamp.from(extent.last()));
    }

    private static void sampleConditions(StringBuilder sql, List<Object> args, SampleMetric metric, TimeWindow window) {
        condition(sql, args, " AND p.metric=?", metric == null ? null : metric.name());
        condition(sql, args, " AND p.captured_at>=?", timestamp(window.from()));
        condition(sql, args, " AND p.captured_at<?", timestamp(window.to()));
    }

    private static void sessionConditions(StringBuilder sql, List<Object> args, SessionFilter filter) {
        condition(sql, args, " AND s.game_id=?", filter.gameId());
        condition(sql, args, " AND s.hardware_profile_id=?", filter.hardwareProfileId());
        condition(sql, args, " AND s.device_link_id=?", filter.deviceLinkId());
        condition(sql, args, " AND s.status=?", filter.status() == null ? null : filter.status().name());
        condition(sql, args, " AND s.started_at>=?", timestamp(filter.startedAt().from()));
        condition(sql, args, " AND s.started_at<?", timestamp(filter.startedAt().to()));
    }

    /** Değer verilmişse sabit koşul parçasını ve bağlı parametreyi ekler. */
    private static void condition(StringBuilder sql, List<Object> args, String clause, Object value) {
        if (value == null) return;
        sql.append(clause);
        args.add(value);
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }

    private static MetricStatsResponse stats(ResultSet row) throws SQLException {
        var metric = SampleMetric.valueOf(row.getString(1));
        return new MetricStatsResponse(metric.name(), metric.unit(), row.getLong(2), row.getLong(3), row.getDouble(4),
                row.getDouble(5), row.getDouble(6), row.getTimestamp(7).toInstant(), row.getTimestamp(8).toInstant());
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
