package com.ltz.performance.application.service;

import com.ltz.performance.api.dto.request.ReportRequest;
import com.ltz.performance.api.dto.response.ReportResponse;
import com.ltz.performance.domain.entity.PerformanceReport;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceNotFoundException;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceReportRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceSessionMaintenance;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceSessionRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceWriteLimits;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tamamlanmış oturum için elle girilmiş tek rapor. Ölçülmemiş değerler doldurulmaz. Oturum başkasınındır veya
 * yoksa ve rapor yoksa 404 aynıdır (IDOR).
 */
@Service
public class PerformanceReportService {
    /** {@code created} yeni kayıt (201), aksi halde güncelleme veya değişmeyen tekrar (200) demektir. */
    public record SaveResult(ReportResponse report, boolean created) { }

    private final PerformanceSessionRepository sessions;
    private final PerformanceReportRepository reports;
    private final PerformanceSessionMaintenance maintenance;
    private final PerformanceAccountAccessGuard guard;
    private final PerformanceWriteLimits limits;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PerformanceReportService(PerformanceSessionRepository sessions, PerformanceReportRepository reports,
            PerformanceSessionMaintenance maintenance, PerformanceAccountAccessGuard guard,
            PerformanceWriteLimits limits, TransactionTemplate transaction, Clock clock) {
        this.sessions = sessions;
        this.reports = reports;
        this.maintenance = maintenance;
        this.guard = guard;
        this.limits = limits;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** İdempotent upsert: aynı değerlerle tekrar çağrı kaydı değiştirmez. */
    public SaveResult put(UUID userId, UUID sessionId, ReportRequest request, String peer) {
        limits.session(userId, peer);
        var values = request.toValues();
        return transaction.execute(_ -> {
            guard.requireActive(userId);
            var now = clock.instant();
            maintenance.abandonStale(userId, now);
            var session = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(PerformanceNotFoundException::new);
            if (!session.isCompleted()) {
                throw new PerformanceConflictException(PerformanceConflictException.SESSION_NOT_COMPLETED);
            }
            var existing = reports.findBySessionIdAndUserId(sessionId, userId).orElse(null);
            if (existing == null) {
                var saved = reports.saveAndFlush(PerformanceReport.createManual(sessionId, userId, values, now));
                return new SaveResult(ReportResponse.from(saved), true);
            }
            if (existing.replace(values, now)) existing = reports.saveAndFlush(existing);
            return new SaveResult(ReportResponse.from(existing), false);
        });
    }

    @Transactional(readOnly = true)
    public ReportResponse get(UUID userId, UUID sessionId) {
        guard.requireActive(userId);
        return reports.findBySessionIdAndUserId(sessionId, userId).map(ReportResponse::from)
                .orElseThrow(PerformanceNotFoundException::new);
    }

    /** Oturum korunur, yalnız rapor silinir; tekrar çağrı 404'tür. */
    public void delete(UUID userId, UUID sessionId, String peer) {
        limits.session(userId, peer);
        transaction.executeWithoutResult(_ -> {
            guard.requireActive(userId);
            if (reports.deleteOwned(sessionId, userId) == 0) throw new PerformanceNotFoundException();
        });
    }
}
