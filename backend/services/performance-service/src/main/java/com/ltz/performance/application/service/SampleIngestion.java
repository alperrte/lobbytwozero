package com.ltz.performance.application.service;

import com.ltz.performance.domain.entity.SampleBatchRules;
import com.ltz.performance.domain.entity.TelemetrySample;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceNotFoundException;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceSampleRepository;
import com.ltz.performance.infrastructure.persistence.repository.SyncBatchRepository.SampleOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Doğrulanmış v2 gönderiminin örneklerini oturuma yazar. Çağıranın açtığı işlem ve hesap kilidi içinde çalışır
 * (kendi işlemi yoktur): hata durumunda hiçbir örnek ve sayaç değişikliği kalmaz (hep-ya-hiç).
 */
@Component
public class SampleIngestion {
    private final PerformanceSampleRepository samples;

    public SampleIngestion(PerformanceSampleRepository samples) { this.samples = samples; }

    /**
     * Sıra: oturum sahipliği (başkasınınki/yoksa tek tip 404) → oturum durumu (409) → örnek değerleri ve zamanları
     * (422, ihlal eden örneğin sırasıyla) → oturum ve hesap kotası (409) → yazım.
     */
    public SampleOutcome ingest(UUID sessionId, UUID userId, UUID deviceLinkId, UUID batchId,
            List<TelemetrySample> batch, Instant now) {
        var session = samples.lockOwnedSession(sessionId, userId).orElseThrow(PerformanceNotFoundException::new);
        SampleBatchRules.requireAccepting(session.status(), session.startedAt(), session.endedAt(), now);
        SampleBatchRules.validate(batch, session.startedAt(), session.endedAt(), now);
        if ((long) session.sampleCount() + batch.size() > PerformanceSampleRepository.MAX_PER_SESSION) {
            throw new PerformanceConflictException(PerformanceConflictException.SESSION_SAMPLE_LIMIT_REACHED);
        }
        if (samples.countForUser(userId) + batch.size() > PerformanceSampleRepository.MAX_PER_USER) {
            throw new PerformanceConflictException(PerformanceConflictException.USER_SAMPLE_QUOTA_REACHED);
        }
        int stored = 0;
        if (!batch.isEmpty()) {
            stored = samples.insertIgnoringDuplicates(
                    new PerformanceSampleRepository.Target(sessionId, userId, deviceLinkId, batchId), batch, now);
            if (stored > 0) samples.addToSessionCount(sessionId, stored);
        }
        return new SampleOutcome(sessionId, stored, batch.size() - stored);
    }
}
