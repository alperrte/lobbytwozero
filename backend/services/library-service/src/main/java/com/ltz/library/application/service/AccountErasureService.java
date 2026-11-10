package com.ltz.library.application.service;

import java.util.Objects;
import java.util.UUID;
import com.ltz.library.infrastructure.persistence.repository.LibraryEntryRepository;
import com.ltz.library.infrastructure.persistence.repository.LibrarySummaryOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountErasureService {
    private final LibraryEntryRepository entries;
    private final LibrarySummaryOutboxRepository summaries;

    public AccountErasureService(LibraryEntryRepository entries, LibrarySummaryOutboxRepository summaries) {
        this.entries = entries;
        this.summaries = summaries;
    }

    /** Koleksiyon, favori/bitirildi bilgileri ve bekleyen özet işareti tek yerel transaction'da silinir; silinmiş hesap için olay üretilmez. */
    @Transactional(timeout = 4)
    public void erase(UUID accountId) {
        UUID account = Objects.requireNonNull(accountId);
        entries.eraseAccount(account);
        summaries.erase(account);
    }
}
