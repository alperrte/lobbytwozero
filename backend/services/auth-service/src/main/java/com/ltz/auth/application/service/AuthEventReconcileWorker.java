package com.ltz.auth.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Eksik hesap durumu olaylarını periyodik yeniden yazar. Hedef listesi boşsa hiçbir şey yapmaz. Çok örnekte güvenlidir
 * (hesap satırları SKIP LOCKED ile kilitlenir). {@code ltz.auth.events.reconcile.worker-enabled=false} ile kapatılır.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ltz.auth.events.reconcile", name = "worker-enabled", havingValue = "true",
        matchIfMissing = true)
public class AuthEventReconcileWorker {

    private final AuthEventReconciler reconciler;

    @Scheduled(fixedDelayString = "${ltz.auth.events.reconcile.poll-interval}")
    void run() {
        try {
            int republished = reconciler.reconcile();
            if (republished > 0) {
                log.info("Auth event reconcile republished events={}", republished);
            }
        } catch (RuntimeException exception) {
            // Döngü çökmemeli; ayrıntı (kişisel veri içerebilir) yazılmaz, yalnız tür.
            log.warn("Auth event reconcile failed cause={}", exception.getClass().getSimpleName());
        }
    }
}
