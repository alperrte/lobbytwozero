package com.ltz.game.infrastructure.config;

import com.ltz.game.application.service.GamePriceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods=false) @EnableScheduling
@ConditionalOnProperty(name="ltz.game.pricing.sync.enabled",havingValue="true",matchIfMissing=true)
public class GamePriceSchedulingConfiguration {
    private final GamePriceService prices;
    public GamePriceSchedulingConfiguration(GamePriceService prices) { this.prices=prices; }
    @Scheduled(fixedDelay=60000,initialDelay=30000) public void refresh() { prices.refresh(); }
}
