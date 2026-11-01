package com.ltz.messaging.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.ltz.messaging.application.port.BlockCheckPort;
import com.ltz.messaging.application.port.ServiceTokenPort;
import com.ltz.messaging.infrastructure.client.AuthServiceTokenClient;
import com.ltz.messaging.infrastructure.client.SocialBlockCheckClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AuthProperties.class,
        GroupProperties.class,
        RequestLimitProperties.class,
        ServiceClientProperties.class
})
public class MessagingApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Giden çağrılar için Auth'tan hedefe bağlı servis token'ı alır (client credentials). */
    @Bean
    ServiceTokenPort authServiceTokenClient(RestClient.Builder builder, Clock clock,
                                            ServiceClientProperties properties) {
        return new AuthServiceTokenClient(builder, clock, properties);
    }

    /**
     * Engel durumu social-service'ten sorulur (MSG-15). Adaptör {@code ltz.messaging.blocks.adapter} ile seçilir;
     * üretimde tek değer {@code social}'dır, testler kendi sahte adaptörünü bağlar.
     */
    @Bean
    @ConditionalOnProperty(name = "ltz.messaging.blocks.adapter", havingValue = "social", matchIfMissing = true)
    BlockCheckPort socialBlockCheckClient(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                          ServiceClientProperties properties) {
        return new SocialBlockCheckClient(builder, serviceTokens, properties);
    }
}
