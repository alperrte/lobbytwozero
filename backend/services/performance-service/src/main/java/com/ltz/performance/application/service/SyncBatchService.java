package com.ltz.performance.application.service;

import com.ltz.performance.api.dto.request.SampleItemRequest;
import com.ltz.performance.api.dto.request.SyncBatchRequest;
import com.ltz.performance.api.dto.response.SyncBatchResponse;
import com.ltz.performance.application.port.AuthenticatedPerformanceDevice;
import com.ltz.performance.domain.entity.PerformanceDigest;
import com.ltz.performance.domain.entity.TelemetrySample;
import com.ltz.performance.domain.enums.SampleMetric;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceForbiddenException;
import com.ltz.performance.domain.exception.PerformanceUnprocessableException;
import com.ltz.performance.infrastructure.persistence.repository.DeviceLinkRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceWriteLimits;
import com.ltz.performance.infrastructure.persistence.repository.SyncBatchRepository;
import com.ltz.performance.infrastructure.persistence.repository.SyncBatchRepository.BatchContent;
import com.ltz.performance.infrastructure.persistence.repository.SyncBatchRepository.BatchKey;
import com.ltz.performance.infrastructure.persistence.repository.SyncBatchRepository.NewBatch;
import com.ltz.performance.infrastructure.persistence.repository.SyncBatchRepository.SampleOutcome;
import com.ltz.performance.infrastructure.persistence.repository.SyncConsentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cihaz token'ıyla gelen toplu gönderimin kabulü. Sıra: şekil denetimi → hız sınırı (işlem dışı) → örnek ayrıştırma
 * → hesap kilidi → izin → cihaz bağı → zaman penceresi → Idempotency-Key makbuzu → (v2) örneklerin yazımı.
 * Aynı anahtar ve aynı içerik özeti tekrar gönderimi (replay) olarak ilk sonucu döner; aynı anahtar farklı içerik 409
 * verir. Kilit altında olduğu için eşzamanlı aynı-anahtar istekleri sıraya girer ve tek kayıt oluşur. Makbuz ve
 * örnekler aynı işlemdedir: hata olursa hiçbiri kalmaz.
 */
@Service
public class SyncBatchService {
    static final Duration MAX_CLIENT_FUTURE_SKEW = Duration.ofMinutes(5);
    static final Duration MAX_CLIENT_AGE = Duration.ofHours(24);

    private final SyncConsentRepository consents;
    private final DeviceLinkRepository links;
    private final SyncBatchRepository batches;
    private final SampleIngestion ingestion;
    private final PerformanceAccountAccessGuard guard;
    private final PerformanceWriteLimits limits;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SyncBatchService(SyncConsentRepository consents, DeviceLinkRepository links, SyncBatchRepository batches,
            SampleIngestion ingestion, PerformanceAccountAccessGuard guard, PerformanceWriteLimits limits,
            TransactionTemplate transaction, Clock clock) {
        this.consents = consents;
        this.links = links;
        this.batches = batches;
        this.ingestion = ingestion;
        this.guard = guard;
        this.limits = limits;
        this.transaction = transaction;
        this.clock = clock;
    }

    public SyncBatchResponse accept(AuthenticatedPerformanceDevice device, UUID idempotencyKey,
            SyncBatchRequest request, String peer) {
        request.requireShape();
        limits.sync(device.userId(), device.deviceId(), peer);
        var parsed = request.isV2() ? parse(request.samples()) : List.<TelemetrySample>of();
        var requestHash = PerformanceDigest.hash(canonical(request, parsed));
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
                return response(stored, true);
            }
            if (!batches.hasCapacity(link.getId(), now)) {
                throw new PerformanceConflictException(PerformanceConflictException.SYNC_BATCH_LIMIT_REACHED);
            }
            var batchId = UUID.randomUUID();
            var outcome = request.isV2()
                    ? ingestion.ingest(request.sessionId(), device.userId(), link.getId(), batchId, parsed, now)
                    : SampleOutcome.NONE;
            var created = batches.store(new NewBatch(
                    new BatchKey(batchId, link.getId(), device.userId(), idempotencyKey),
                    new BatchContent(requestHash, request.schemaVersion(), request.effectiveSampleCount(),
                            request.clientSentAt()),
                    outcome), now);
            link.recordSync(now);
            links.save(link);
            return response(created, false);
        });
    }

    private static SyncBatchResponse response(SyncBatchRepository.StoredBatch batch, boolean replayed) {
        return new SyncBatchResponse(batch.id(), batch.sampleCount(), batch.storedCount(), batch.duplicateCount(),
                batch.receivedAt(), replayed);
    }

    /** Metrik adı yalnız kanonik adla eşleşir; bilinmeyen metrik ihlal eden örneğin sırasıyla 422 verir. */
    private static List<TelemetrySample> parse(List<SampleItemRequest> items) {
        var parsed = new ArrayList<TelemetrySample>(items.size());
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            var metric = SampleMetric.parse(item.metric());
            if (metric == null) {
                throw new PerformanceUnprocessableException(
                        PerformanceUnprocessableException.SAMPLE_METRIC_UNSUPPORTED, index);
            }
            parsed.add(new TelemetrySample(item.capturedAt(), metric, item.value()));
        }
        return parsed;
    }

    /** v1 özeti Faz 1 ile aynıdır; v2 oturumu, zarf zamanını ve örnekleri gönderilme sırasıyla kapsar. */
    private static String canonical(SyncBatchRequest request, List<TelemetrySample> parsed) {
        if (!request.isV2()) return request.canonicalV1();
        var builder = new StringBuilder("v2|").append(request.sessionId()).append('|')
                .append(request.clientSentAt().toEpochMilli());
        for (var sample : parsed) {
            builder.append('|').append(sample.capturedAt().toEpochMilli()).append(',')
                    .append(sample.metric().name()).append(',').append(sample.value());
        }
        return builder.toString();
    }

    private static void requireClientTimeInWindow(Instant clientSentAt, Instant now) {
        if (clientSentAt.isAfter(now.plus(MAX_CLIENT_FUTURE_SKEW)) || clientSentAt.isBefore(now.minus(MAX_CLIENT_AGE))) {
            throw new PerformanceUnprocessableException(PerformanceUnprocessableException.SYNC_CLIENT_TIME_INVALID);
        }
    }
}
