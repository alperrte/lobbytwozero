package com.ltz.auth.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Süresi dolan askıları periyodik açar. {@code ltz.auth.suspension.worker-enabled=false} ile kapatılır. */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ltz.auth.suspension", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class SuspensionExpiryWorker {

    private final SuspensionExpiryService expiryService;

    @Scheduled(fixedDelayString = "${ltz.auth.suspension.poll-interval}")
    void run() {
        try {
            int reopened = expiryService.expireDue();
            if (reopened > 0) {
                log.info("Suspension expiry reopened accounts={}", reopened);
            }
        } catch (RuntimeException exception) {
            // Döngü çökmemeli; ayrıntı yazılmaz, yalnız tür.
            log.warn("Suspension expiry failed cause={}", exception.getClass().getSimpleName());
        }
    }
}
