package com.ltz.mail.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Zamanlanmış saklama temizleyicisi. Bir tur hata verirse zamanlama durmaz. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ltz.mail.worker.enabled", havingValue = "true", matchIfMissing = true)
public class MailRetentionWorker {

    private final MailRetentionService retention;

    @Scheduled(fixedDelayString = "${ltz.mail.worker.retention-interval:PT1H}",
            initialDelayString = "${ltz.mail.worker.retention-interval:PT1H}")
    void run() {
        try {
            int removed = retention.purge();
            if (removed > 0) {
                log.info("Mail retention finished removedMessages={}", removed);
            }
        } catch (RuntimeException exception) {
            log.warn("Mail retention failed; it will be retried cause={}", exception.getClass().getSimpleName());
        }
    }
}
