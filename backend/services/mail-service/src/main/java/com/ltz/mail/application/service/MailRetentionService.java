package com.ltz.mail.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.mail.infrastructure.config.WorkerProperties;
import com.ltz.mail.infrastructure.persistence.repository.MailMessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Saklama süresi (docs/database.md §5): gönderilmiş ({@code SENT}) ve kalıcı başarısız ({@code FAILED}) kayıtlar
 * {@code retention} sonra silinir; deneme geçmişi veritabanı seviyesinde {@code ON DELETE CASCADE} ile gider.
 */
@Service
@RequiredArgsConstructor
public class MailRetentionService {

    private final MailMessageRepository messages;
    private final WorkerProperties worker;
    private final Clock clock;

    /** @return bu turda silinen kayıt sayısı */
    @Transactional
    public int purge() {
        Instant cutoff = clock.instant().minus(worker.retention());
        return messages.purgeTerminalBefore(cutoff, worker.retentionBatch());
    }
}
