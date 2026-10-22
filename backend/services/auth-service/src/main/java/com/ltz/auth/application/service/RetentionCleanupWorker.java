package com.ltz.auth.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Temizliği periyodik çalıştırır. {@code ltz.auth.cleanup.enabled=false} ile kapatılır. */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ltz.auth.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetentionCleanupWorker {

    private final RetentionCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${ltz.auth.cleanup.interval}")
    void run() {
        try {
            RetentionCleanupService.CleanupResult result = cleanupService.run();
            if (result.total() > 0) {
                log.info("Retention cleanup pendingAccounts={} ephemeralRows={} securityEvents={}",
                        result.expiredPendingAccounts(), result.ephemeralRows(), result.securityEvents());
            }
        } catch (RuntimeException exception) {
            // Döngü çökmemeli; ayrıntı yazılmaz, yalnız tür.
            log.warn("Retention cleanup failed cause={}", exception.getClass().getSimpleName());
        }
    }
}
