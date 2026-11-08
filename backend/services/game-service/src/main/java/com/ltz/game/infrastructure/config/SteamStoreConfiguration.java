package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.StoreDetailSource;
import com.ltz.game.infrastructure.client.SteamStoreClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Steam mağaza istemcisi: yalnız bir {@link RestClient} kurar, kendiliğinden istek yapmaz (çağıran servis zamanlayıcıyla sürer). */
@Configuration(proxyBeanMethods = false)
public class SteamStoreConfiguration {
    @Bean
    StoreDetailSource steamStoreSource(RestClient.Builder builder,
            @Value("${ltz.game.steam-store.url:https://store.steampowered.com}") String url,
            @Value("${ltz.game.steam-store.min-interval:PT1.7S}") Duration minInterval) {
        RestClient http = GameRatingConfiguration.restClient(builder, url, Duration.ofSeconds(3), Duration.ofSeconds(10));
        return new SteamStoreClient(http, minInterval);
    }
}
