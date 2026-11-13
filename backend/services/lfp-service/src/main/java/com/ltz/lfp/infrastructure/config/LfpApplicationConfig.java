package com.ltz.lfp.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.ltz.lfp.application.port.GameCatalogPort;
import com.ltz.lfp.infrastructure.client.GameServiceClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AuthProperties.class,
        ListingProperties.class,
        RequestLimitProperties.class,
        ServiceClientProperties.class
})
public class LfpApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Oyun doğrulaması (LFP-02, LFP-18): game-service'in herkese açık kart ucu. Adaptör {@code ltz.lfp.games.adapter}
     * ile seçilir; üretimdeki tek değer {@code game}'dir, testler kendi sahte adaptörünü bağlar.
     */
    @Bean
    @ConditionalOnProperty(name = "ltz.lfp.games.adapter", havingValue = "game", matchIfMissing = true)
    GameCatalogPort gameServiceClient(RestClient.Builder builder, ServiceClientProperties properties) {
        return new GameServiceClient(builder, properties);
    }
}
