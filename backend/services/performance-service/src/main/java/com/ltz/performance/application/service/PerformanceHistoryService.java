package com.ltz.performance.application.service;

import com.ltz.performance.api.dto.response.AccountStatsResponse;
import com.ltz.performance.api.dto.response.MetricStatsResponse;
import com.ltz.performance.api.dto.response.PerformanceReferenceResponse;
import com.ltz.performance.api.dto.response.PerformanceSummaryResponse;
import com.ltz.performance.api.dto.response.SamplePageResponse;
import com.ltz.performance.api.dto.response.SeriesResponse;
import com.ltz.performance.api.dto.response.SessionStatsResponse;
import com.ltz.performance.domain.enums.SampleMetric;
import com.ltz.performance.domain.enums.SessionStatus;
import com.ltz.performance.domain.exception.PerformanceNotFoundException;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceSampleRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceSessionRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceStatsRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geçmiş, istatistik ve grafik okumaları (PERF-14/15/16/19) ile servisler arası özet sözleşmeleri (PERF-21).
 * Yalnız salt okunur sorgular; tüm sorgular hesaba (user_id) bağlıdır. Bu servis başka servislerin verisini okumaz.
 */
@Service
public class PerformanceHistoryService {
    public static final int DEFAULT_SAMPLE_PAGE = 100;
    public static final int MAX_SAMPLE_PAGE = 500;
    public static final int DEFAULT_POINTS = 300;
    public static final int MIN_POINTS = 10;
    public static final int MAX_POINTS = 1000;
    /** Recommendation referansı için en az FPS örneği; altında {@code reliable=false} döner (varsayım: I-24). */
    public static final long MIN_RELIABLE_FPS_SAMPLES = 60;

    private final PerformanceSessionRepository sessions;
    private final PerformanceStatsRepository stats;
    private final PerformanceAccountAccessGuard guard;

    public PerformanceHistoryService(PerformanceSessionRepository sessions, PerformanceStatsRepository stats,
            PerformanceAccountAccessGuard guard) {
        this.sessions = sessions;
        this.stats = stats;
        this.guard = guard;
    }

    /** Bilinmeyen metrik adı 400'dür; boş değer "tümü" demektir. */
    public static SampleMetric parseMetric(String name) {
        if (name == null) return null;
        var metric = SampleMetric.parse(name);
        if (metric == null) throw new IllegalArgumentException("unknown metric");
        return metric;
    }

    @Transactional(readOnly = true)
    public SamplePageResponse samples(UUID userId, UUID sessionId, SampleMetric metric, TimeWindow window, int page,
            int size) {
        if (page < 0 || size < 1 || size > MAX_SAMPLE_PAGE) throw new IllegalArgumentException("Invalid page");
        requireOwned(userId, sessionId);
        long offset = (long) page * size;
        if (offset >= PerformanceSampleRepository.MAX_PER_SESSION) return new SamplePageResponse(List.of(), page, size, false);
        var rows = stats.samples(sessionId, userId, metric, window, (int) offset, size + 1);
        boolean hasNext = rows.size() > size;
        return new SamplePageResponse(hasNext ? rows.subList(0, size) : rows, page, size, hasNext);
    }

    @Transactional(readOnly = true)
    public SessionStatsResponse sessionStats(UUID userId, UUID sessionId, SampleMetric metric, TimeWindow window) {
        requireOwned(userId, sessionId);
        var metrics = stats.sessionStats(sessionId, userId, metric, window);
        return new SessionStatsResponse(sessionId, metrics.stream().mapToLong(MetricStatsResponse::count).sum(), metrics);
    }

    @Transactional(readOnly = true)
    public AccountStatsResponse accountStats(UUID userId, SessionFilter filter, SampleMetric metric) {
        guard.requireActive(userId);
        var metrics = stats.accountStats(userId, filter, metric);
        return new AccountStatsResponse(stats.countSessions(userId, filter),
                metrics.stream().mapToLong(MetricStatsResponse::count).sum(), metrics);
    }

    /**
     * Tek metrik için zaman serisi. Örnek sayısı {@code maxPoints}'i aşmazsa ham noktalar döner; aşarsa örnekler
     * eşit genişlikli kovalara bölünüp (ortalama/min/max/sayı) özetlenir ve yanıt bunu açıkça belirtir. Nokta sayısı
     * hiçbir zaman {@code maxPoints}'i aşmaz; aralık uzunluğu veya veri hacmi yanıt boyutunu etkilemez.
     */
    @Transactional(readOnly = true)
    public SeriesResponse series(UUID userId, UUID sessionId, SampleMetric metric, TimeWindow window, int maxPoints) {
        if (metric == null || maxPoints < MIN_POINTS || maxPoints > MAX_POINTS) {
            throw new IllegalArgumentException("Invalid series request");
        }
        requireOwned(userId, sessionId);
        var extent = stats.extent(sessionId, userId, metric, window);
        if (extent.count() == 0) {
            return new SeriesResponse(sessionId, metric.name(), metric.unit(), "RAW", false, null, "NONE", 0, List.of());
        }
        if (extent.count() <= maxPoints) {
            var points = stats.rawPoints(sessionId, userId, metric, extent, maxPoints);
            return new SeriesResponse(sessionId, metric.name(), metric.unit(), "RAW", false, null, "NONE",
                    extent.count(), points);
        }
        long span = extent.last().toEpochMilli() - extent.first().toEpochMilli();
        long bucketMillis = Math.max(1, Math.ceilDiv(span + 1, maxPoints));
        var points = stats.bucketPoints(sessionId, userId, metric, extent, bucketMillis);
        return new SeriesResponse(sessionId, metric.name(), metric.unit(), "BUCKET", true, bucketMillis,
                "AVG_MIN_MAX", extent.count(), points);
    }

    /** Hardware sözleşmesi: bu hesabın, verilen donanım profiliyle tamamlanmış oturumlarının metrik özeti. */
    @Transactional(readOnly = true)
    public PerformanceSummaryResponse hardwareSummary(UUID accountId, UUID hardwareProfileId) {
        guard.requireActive(accountId);
        var filter = new SessionFilter(null, hardwareProfileId, null, SessionStatus.COMPLETED, null);
        var metrics = stats.accountStats(accountId, filter, null);
        Instant last = metrics.stream().map(MetricStatsResponse::lastAt).max(Comparator.naturalOrder()).orElse(null);
        return new PerformanceSummaryResponse(hardwareProfileId, stats.countSessions(accountId, filter),
                metrics.stream().mapToLong(MetricStatsResponse::count).sum(), last, metrics);
    }

    /**
     * Recommendation sözleşmesi: bu hesabın bir oyundaki FPS referansı (yalnız tamamlanmış oturumlar). Örnek sayısı
     * {@link #MIN_RELIABLE_FPS_SAMPLES} altındaysa değerler {@code null}, {@code reliable=false} döner.
     */
    @Transactional(readOnly = true)
    public PerformanceReferenceResponse gameReference(UUID accountId, UUID gameId, UUID hardwareProfileId) {
        guard.requireActive(accountId);
        var filter = new SessionFilter(gameId, hardwareProfileId, null, SessionStatus.COMPLETED, null);
        var fps = stats.accountStats(accountId, filter, SampleMetric.FPS).stream().findFirst().orElse(null);
        if (fps == null) {
            return new PerformanceReferenceResponse(gameId, hardwareProfileId, false, 0, 0, null, null, null, null);
        }
        boolean reliable = fps.count() >= MIN_RELIABLE_FPS_SAMPLES;
        return new PerformanceReferenceResponse(gameId, hardwareProfileId, reliable, fps.sessionCount(), fps.count(),
                reliable ? fps.avg() : null, reliable ? fps.min() : null, reliable ? fps.max() : null, fps.lastAt());
    }

    private void requireOwned(UUID userId, UUID sessionId) {
        guard.requireActive(userId);
        sessions.findByIdAndUserId(sessionId, userId).orElseThrow(PerformanceNotFoundException::new);
    }
}
