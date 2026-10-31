package com.ltz.performance.application.service;

import com.ltz.performance.api.dto.response.SyncConsentResponse;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceWriteLimits;
import com.ltz.performance.infrastructure.persistence.repository.SyncConsentRepository;
import com.ltz.performance.domain.entity.SyncConsent;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SyncConsentService {
    private final SyncConsentRepository consents;
    private final PerformanceAccountAccessGuard guard;
    private final PerformanceWriteLimits limits;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SyncConsentService(SyncConsentRepository consents, PerformanceAccountAccessGuard guard,
            PerformanceWriteLimits limits, TransactionTemplate transaction, Clock clock) {
        this.consents = consents;
        this.guard = guard;
        this.limits = limits;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SyncConsentResponse get(UUID userId) {
        guard.requireActive(userId);
        return consents.findById(userId).map(SyncConsentResponse::from).orElseGet(SyncConsentResponse::none);
    }

    /** İdempotent: zaten verilmiş izin değişmez. */
    public SyncConsentResponse grant(UUID userId, String peer) {
        limits.link(userId, peer);
        return transaction.execute(_ -> {
            guard.requireActive(userId);
            var now = clock.instant();
            var consent = consents.findById(userId).orElse(null);
            if (consent == null) consent = SyncConsent.granted(userId, now);
            else consent.grant(now);
            return SyncConsentResponse.from(consents.save(consent));
        });
    }

    /** İdempotent: kayıt yoksa iptal edilecek bir şey yoktur. */
    public SyncConsentResponse revoke(UUID userId, String peer) {
        limits.link(userId, peer);
        return transaction.execute(_ -> {
            guard.requireActive(userId);
            return consents.findById(userId).map(consent -> {
                consent.revoke(clock.instant());
                return SyncConsentResponse.from(consents.save(consent));
            }).orElseGet(SyncConsentResponse::none);
        });
    }
}
