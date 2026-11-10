package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.infrastructure.client.SteamGameProvider;
import com.ltz.game.infrastructure.client.SteamRequestBudget;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class SteamProviderConfiguration {

    @Bean
    GameProvider steamGameProvider(
            RestClient.Builder builder,
            @Value("${STEAM_WEB_API_KEY:}") String key,
            @Value("${STEAM_CATALOG_ENABLED:false}") boolean enabled,
            @Value("${ltz.game.steam.min-interval-millis:1000}") long minIntervalMillis,
            @Value("${ltz.game.steam.daily-budget:50000}") int dailyBudget) {
        if (enabled && key.isBlank()) {
            throw new IllegalStateException("Steam kataloğu etkin ancak API anahtarı yapılandırılmamış.");
        }
        return new SteamGameProvider(builder.build(), key, enabled, new SteamRequestBudget(Clock.systemUTC(), minIntervalMillis, dailyBudget));
    }
}
