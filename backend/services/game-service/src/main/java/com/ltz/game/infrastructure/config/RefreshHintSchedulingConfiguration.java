package com.ltz.game.infrastructure.config;

import com.ltz.game.application.service.RefreshHintDrain;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Sayfası açılan oyunların ipucu kuyruğunu boşaltır. Kuyruk boşsa tur tek bir bellek içi kontroldür; dış istek yalnız kota sınırları içinde yapılır. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ltz.game.refresh.hints.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class RefreshHintSchedulingConfiguration {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RefreshHintSchedulingConfiguration.class);
    private final RefreshHintDrain drain;

    public RefreshHintSchedulingConfiguration(RefreshHintDrain drain) { this.drain = drain; }

    @Scheduled(fixedDelayString = "${ltz.game.refresh.hints.drain-interval:PT10S}", initialDelay = 60000)
    public void drain() {
        try {
            drain.drain();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            LOG.warn("İpucu yenilemesi eşzamanlı değişiklik nedeniyle geri alındı; oyun planlı turda yenilenecek.");
        } catch (RuntimeException error) {
            // En iyi çaba: bir ipucu turunun hatası zamanlayıcıyı durdurmaz ve kullanıcı akışına yansımaz.
            LOG.warn("İpucu yenilemesi başarısız: {}", error.getClass().getSimpleName());
        }
    }
}
