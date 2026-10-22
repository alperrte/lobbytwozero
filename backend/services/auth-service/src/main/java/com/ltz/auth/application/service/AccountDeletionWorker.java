package com.ltz.auth.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bekleyen silme görevlerini periyodik işler. Tek örnek yeterlidir ancak çoklu örnekte de güvenlidir
 * (görevler SKIP LOCKED ile kiralanır). {@code ltz.auth.deletion.worker-enabled=false} ile kapatılır.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ltz.auth.deletion", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class AccountDeletionWorker {

    private final AccountDeletionCoordinator coordinator;

    @Scheduled(fixedDelayString = "${ltz.auth.deletion.poll-interval}")
    void run() {
        try {
            coordinator.processDue();
        } catch (RuntimeException exception) {
            // Döngü çökmemeli; ayrıntı (kişisel veri içerebilir) yazılmaz, yalnız tür.
            log.warn("Account deletion worker run failed cause={}", exception.getClass().getSimpleName());
        }
    }
}
