package com.ltz.game.infrastructure.config;

import com.ltz.game.application.service.DlcParentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** DLC → ana oyun ilişkisi incelemesi (katalog zamanlayıcısıyla aynı anahtar). Biten iş tek sorguluk boş turdur. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "game.catalog.sync.enabled", havingValue = "true")
@EnableScheduling
public class DlcParentSchedulingConfiguration {
    /** Tur başına en çok bu kadar adım (IGDB partisi 200 DLC, Steam adımı en çok 3 AppID). */
    static final int STEPS = 2;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DlcParentSchedulingConfiguration.class);

    private final DlcParentService service;

    public DlcParentSchedulingConfiguration(DlcParentService service) { this.service = service; }

    @Scheduled(fixedDelay = 3000, initialDelay = 60000)
    public void review() {
        try {
            for (int step = 0; step < STEPS && service.reviewNext() >= 0; step++) {
                // -1: iş yok / duraklatıldı.
            }
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            LOG.warn("DLC ilişki partisi eşzamanlı değişiklik nedeniyle geri alındı; sonraki turda yeniden denenecek.");
        }
    }
}
