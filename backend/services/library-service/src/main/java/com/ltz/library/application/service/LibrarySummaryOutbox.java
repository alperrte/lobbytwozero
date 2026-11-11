package com.ltz.library.application.service;

import com.ltz.library.infrastructure.persistence.repository.LibraryEntryRepository;
import com.ltz.library.infrastructure.persistence.repository.LibrarySummaryOutboxRepository;
import com.ltz.library.infrastructure.persistence.repository.LibrarySummaryOutboxRepository.Claim;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Özet işaretlerinin kısa yerel transaction'ları; HTTP teslimi bu sınıfın dışındadır. */
@Service
public class LibrarySummaryOutbox {
    private final LibrarySummaryOutboxRepository outbox;
    private final LibraryEntryRepository entries;

    public LibrarySummaryOutbox(LibrarySummaryOutboxRepository outbox, LibraryEntryRepository entries) {
        this.outbox = outbox;
        this.entries = entries;
    }

    @Transactional(timeout = 4)
    public List<Claim> claimDue(int batch, long leaseSeconds) { return outbox.claimDue(batch, leaseSeconds); }

    /** Teslimden hemen önce okunan güncel mutlak sayılar. */
    @Transactional(readOnly = true, timeout = 4)
    public LibraryEntryRepository.Counts counts(UUID userId) { return entries.statistics(userId); }

    @Transactional(timeout = 4)
    public boolean acknowledge(UUID userId, long changeSeq) { return outbox.acknowledge(userId, changeSeq); }

    @Transactional(timeout = 4)
    public void fail(UUID userId, long baseSeconds, long maxSeconds) { outbox.fail(userId, baseSeconds, maxSeconds); }
}
