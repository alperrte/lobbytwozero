package com.ltz.performance.application.service;

import com.ltz.performance.domain.exception.PerformanceNotFoundException;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceSampleRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceWriteLimits;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Kullanıcının kendi oturumundaki örnekleri silmesi (gizlilik); oturum ve raporu korunur. */
@Service
public class PerformanceSampleService {
    private final PerformanceSampleRepository samples;
    private final PerformanceAccountAccessGuard guard;
    private final PerformanceWriteLimits limits;
    private final TransactionTemplate transaction;

    public PerformanceSampleService(PerformanceSampleRepository samples, PerformanceAccountAccessGuard guard,
            PerformanceWriteLimits limits, TransactionTemplate transaction) {
        this.samples = samples;
        this.guard = guard;
        this.limits = limits;
        this.transaction = transaction;
    }

    /** Oturum yoksa veya başkasınınsa tek tip 404; örneksiz kendi oturumunda 204 (idempotent). */
    public void deleteForSession(UUID userId, UUID sessionId, String peer) {
        limits.session(userId, peer);
        transaction.executeWithoutResult(_ -> {
            guard.requireActive(userId);
            samples.lockOwnedSession(sessionId, userId).orElseThrow(PerformanceNotFoundException::new);
            samples.deleteForSession(sessionId, userId);
        });
    }
}
