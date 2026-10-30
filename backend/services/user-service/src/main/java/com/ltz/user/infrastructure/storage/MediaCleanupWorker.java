package com.ltz.user.infrastructure.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ltz.user.application.service.MediaCleanupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Zamanlanmış görsel temizleyici. Bir tur hata verirse zamanlama durmaz. {@code ltz.user.media.cleanup-enabled=false} kapatır. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ltz.user.media.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class MediaCleanupWorker {

    private final MediaCleanupService cleanup;

    @Scheduled(fixedDelayString = "${ltz.user.media.cleanup-interval:PT5M}",
            initialDelayString = "${ltz.user.media.cleanup-interval:PT5M}")
    void run() {
        try {
            int removed = cleanup.purge();
            if (removed > 0) {
                log.info("Media cleanup finished: removedRows={}", removed);
            }
        } catch (RuntimeException exception) {
            log.warn("Media cleanup failed; it will be retried", exception);
        }
    }
}
