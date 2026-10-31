package com.ltz.performance.application.service;

import com.ltz.performance.api.dto.request.SyncBatchRequest;
import com.ltz.performance.api.dto.response.SyncBatchResponse;
import com.ltz.performance.application.port.AuthenticatedPerformanceDevice;
import com.ltz.performance.domain.entity.PerformanceDigest;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceForbiddenException;
import com.ltz.performance.domain.exception.PerformanceUnprocessableException;
import com.ltz.performance.infrastructure.persistence.repository.DeviceLinkRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceWriteLimits;
import com.ltz.performance.infrastructure.persistence.repository.SyncBatchRepository;
import com.ltz.performance.infrastructure.persistence.repository.SyncConsentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cihaz token'ıyla gelen toplu gönderimin kabulü. Sıra: hız sınırı (işlem dışı) → hesap kilidi → izin → cihaz bağı
 * → zaman penceresi → Idempotency-Key makbuzu. Aynı anahtar ve aynı içerik özeti tekrar gönderimi (replay) olarak
 * aynı yanıtı döner; aynı anahtar farklı içerik 409 verir. Kilit altında olduğu için eşzamanlı aynı-anahtar
 * istekleri sıraya girer ve tek kayıt oluşur.
 */
@Service
public class SyncBatchService {
    static final Duration MAX_CLIENT_FUTURE_SKEW = Duration.ofMinutes(5);
    static final Duration MAX_CLIENT_AGE = Duration.ofHours(24);

    private final SyncConsentRepository consents;
    private final DeviceLinkRepository links;
    private final SyncBatchRepository batches;
    private final PerformanceAccountAccessGuard guard;
    private final PerformanceWriteLimits limits;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SyncBatchService(SyncConsentRepository consents, DeviceLinkRepository links, SyncBatchRepository batches,
            PerformanceAccountAccessGuard guard, PerformanceWriteLimits limits, TransactionTemplate transaction,
            Clock clock) {
        this.consents = consents;
        this.links = links;
        this.batches = batches;
        this.guard = guard;
        this.limits = limits;
        this.transaction = transaction;
        this.clock = clock;
    }

    public SyncBatchResponse accept(AuthenticatedPerformanceDevice device, UUID idempotencyKey,
            SyncBatchRequest request, String peer) {
        limits.sync(device.userId(), device.deviceId(), peer);
        var requestHash = PerformanceDigest.hash(request.canonical());
        return transaction.execute(_ -> {
            guard.requireActive(device.userId());
            var consent = consents.findById(device.userId()).orElse(null);
            if (consent == null || !consent.isGranted()) {
                throw new PerformanceForbiddenException(PerformanceForbiddenException.SYNC_CONSENT_REQUIRED);
            }
            // Bağ yoksa, başka hesaba aitse veya devre dışıysa aynı genel hata döner (sahiplik sızıntısı yok).
            var link = links.findByAuthDeviceId(device.deviceId())
                    .filter(l -> l.isOwnedBy(device.userId()) && l.isActive())
                    .orElseThrow(() -> new PerformanceForbiddenException(PerformanceForbiddenException.DEVICE_NOT_LINKED));
            var now = clock.instant();
            requireClientTimeInWindow(request.clientSentAt(), now);
            var stored = batches.findLive(link.getId(), idempotencyKey, now).orElse(null);
            if (stored != null) {
                if (!stored.requestHash().equals(requestHash)) {
                    throw new PerformanceConflictException(PerformanceConflictException.IDEMPOTENCY_KEY_REUSED);
                }
                return new SyncBatchResponse(stored.id(), stored.sampleCount(), stored.receivedAt(), true);
            }
            if (!batches.hasCapacity(link.getId(), now)) {
                throw new PerformanceConflictException(PerformanceConflictException.SYNC_BATCH_LIMIT_REACHED);
            }
            var created = batches.store(link.getId(), device.userId(), idempotencyKey, requestHash,
                    request.schemaVersion(), request.sampleCount(), request.clientSentAt(), now);
            link.recordSync(now);
            links.save(link);
            return new SyncBatchResponse(created.id(), created.sampleCount(), created.receivedAt(), false);
        });
    }

    private static void requireClientTimeInWindow(Instant clientSentAt, Instant now) {
        if (clientSentAt.isAfter(now.plus(MAX_CLIENT_FUTURE_SKEW)) || clientSentAt.isBefore(now.minus(MAX_CLIENT_AGE))) {
            throw new PerformanceUnprocessableException(PerformanceUnprocessableException.SYNC_CLIENT_TIME_INVALID);
        }
    }
}
