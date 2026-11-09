package com.ltz.library.application.service;

import com.ltz.library.application.port.LibrarySummaryPublisher;
import com.ltz.library.infrastructure.persistence.repository.LibraryEntryRepository.Counts;
import com.ltz.library.infrastructure.persistence.repository.LibrarySummaryOutboxRepository.Claim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Zamanı gelmiş özet işaretlerini User'a iletir (en az bir kez). Sayılar her teslimde yeniden okunur; tek bir kullanıcının
 * başarısızlığı diğerlerini engellemez. Günlüğe kullanıcı kimliği, yük veya yanıt gövdesi yazılmaz.
 */
@Service
public class LibrarySummaryRelay {
    private static final Logger LOG = LoggerFactory.getLogger(LibrarySummaryRelay.class);

    private final LibrarySummaryOutbox outbox;
    private final LibrarySummaryPublisher publisher;
    private final int batchSize;
    private final long leaseSeconds;
    private final long baseBackoffSeconds;
    private final long maxBackoffSeconds;

    public LibrarySummaryRelay(LibrarySummaryOutbox outbox, LibrarySummaryPublisher publisher,
            @Value("${ltz.library.outbox.batch-size:50}") int batchSize,
            @Value("${ltz.library.outbox.lease-seconds:60}") long leaseSeconds,
            @Value("${ltz.library.outbox.base-backoff-seconds:5}") long baseBackoffSeconds,
            @Value("${ltz.library.outbox.max-backoff-seconds:300}") long maxBackoffSeconds) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.baseBackoffSeconds = baseBackoffSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    /** Bir tur çalışır; başarıyla teslim edilen kullanıcı sayısını döner. */
    public int deliverDue() {
        if (!publisher.ready()) return 0;
        int delivered = 0;
        for (Claim claim : outbox.claimDue(batchSize, leaseSeconds)) {
            try {
                Counts counts = outbox.counts(claim.userId());
                publisher.publish(claim.userId(), Math.toIntExact(counts.total()), Math.toIntExact(counts.completed()),
                        Math.toIntExact(counts.favorite()));
                outbox.acknowledge(claim.userId(), claim.changeSeq());
                delivered++;
            } catch (RuntimeException error) {
                LOG.warn("LibrarySummaryChanged teslim edilemedi: {}", error.getClass().getSimpleName());
                outbox.fail(claim.userId(), baseBackoffSeconds, maxBackoffSeconds);
            }
        }
        return delivered;
    }
}
