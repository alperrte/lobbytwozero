package com.ltz.auth.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bekleyen olayları periyodik teslim eder. Hedef listesi boşsa sorgu boş döner ve maliyeti ihmal edilebilir. Çoklu
 * örnekte de güvenlidir (satırlar SKIP LOCKED ile kiralanır). {@code ltz.auth.events.worker-enabled=false} ile kapatılır.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ltz.auth.events", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class AuthEventWorker {

    private final AuthEventRelay relay;

    @Scheduled(fixedDelayString = "${ltz.auth.events.poll-interval}")
    void run() {
        try {
            relay.processDue();
        } catch (RuntimeException exception) {
            // Döngü çökmemeli; ayrıntı (kişisel veri içerebilir) yazılmaz, yalnız tür.
            log.warn("Auth event worker run failed cause={}", exception.getClass().getSimpleName());
        }
    }
}
