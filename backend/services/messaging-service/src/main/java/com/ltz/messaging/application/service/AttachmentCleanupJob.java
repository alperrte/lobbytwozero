package com.ltz.messaging.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mesaja bağlanmadan bekleyen yüklemeleri düzenli siler (MSG-17); depolama sınırsız büyümez (SECURITY.md §12). Aralık
 * {@code ltz.messaging.attachments.cleanup-interval} ile ayarlanır. Yalnız sayı loglanır, içerik ve kimlik loglanmaz.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCleanupJob {

    private final AttachmentService attachments;

    @Scheduled(initialDelayString = "${ltz.messaging.attachments.cleanup-interval}",
            fixedDelayString = "${ltz.messaging.attachments.cleanup-interval}")
    public void run() {
        int deleted = attachments.deleteExpiredUploads();
        if (deleted > 0) {
            log.info("Deleted {} expired unsent message attachments", deleted);
        }
    }
}
