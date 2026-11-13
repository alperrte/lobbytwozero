package com.ltz.lfp.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import com.ltz.lfp.application.port.BlockCheckPort;
import com.ltz.lfp.application.port.GameCatalogPort;
import com.ltz.lfp.application.port.ServiceTokenPort;
import com.ltz.lfp.infrastructure.client.AuthServiceTokenClient;
import com.ltz.lfp.infrastructure.client.GameServiceClient;
import com.ltz.lfp.infrastructure.client.SocialBlockCheckClient;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({
        AuthProperties.class,
        ExpiryProperties.class,
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

    /** Auth'tan hedefe bağlı servis token'ı (client credentials); gizli anahtar yoksa Auth hiç çağrılmaz. */
    @Bean
    ServiceTokenPort authServiceTokenClient(RestClient.Builder builder, Clock clock,
                                            ServiceClientProperties properties) {
        return new AuthServiceTokenClient(builder, clock, properties);
    }

    /**
     * Engel sorgusu (LFP-17): social-service iç ucu. Adaptör {@code ltz.lfp.blocks.adapter} ile seçilir; üretimdeki
     * tek değer {@code social}'dır, testler kendi sahte adaptörünü bağlar.
     */
    @Bean
    @ConditionalOnProperty(name = "ltz.lfp.blocks.adapter", havingValue = "social", matchIfMissing = true)
    BlockCheckPort socialBlockCheckClient(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                          ServiceClientProperties properties) {
        return new SocialBlockCheckClient(builder, serviceTokens, properties);
    }
}
