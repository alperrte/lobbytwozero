package com.ltz.game.infrastructure.config;

import com.ltz.game.application.service.GameRatingSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * LTZ puan projeksiyonunu periyodik tarar. Auth istemci gizli anahtarı yoksa tarama Review'a hiç istek atmadan
 * atlanır; tazelik sınırı {@code ltz.game.rating.sync.interval} değeridir.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ltz.game.rating.sync.enabled", havingValue = "true")
@EnableScheduling
public class GameRatingSyncSchedulingConfiguration {
    private final GameRatingSyncService sync;

    public GameRatingSyncSchedulingConfiguration(GameRatingSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(fixedDelayString = "${ltz.game.rating.sync.interval:PT5M}",
            initialDelayString = "${ltz.game.rating.sync.initial-delay:PT30S}")
    public void synchronize() {
        sync.synchronize();
    }
}
