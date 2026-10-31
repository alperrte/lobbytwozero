package com.ltz.library.application.service;

import java.util.UUID;
import com.ltz.library.infrastructure.persistence.repository.LibraryEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountErasureService {
    private final LibraryEntryRepository entries;

    public AccountErasureService(LibraryEntryRepository entries) { this.entries = entries; }

    /** Koleksiyon ve içindeki favori/bitirildi bilgileri tek yerel transaction'da silinir. */
    @Transactional(timeout = 4)
    public void erase(UUID accountId) {
        entries.eraseAccount(java.util.Objects.requireNonNull(accountId));
    }
}
