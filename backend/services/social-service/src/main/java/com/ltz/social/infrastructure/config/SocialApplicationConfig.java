package com.ltz.social.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.ltz.social.application.port.SocialEventPublisher;
import com.ltz.social.infrastructure.client.UnavailableEventPublisher;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({
        AuthProperties.class,
        FriendRequestProperties.class,
        InviteCodeProperties.class,
        OutboxProperties.class,
        RequestLimitProperties.class
})
public class SocialApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Gerçek teslim adaptörü yazılana kadar varsayılan; adaptör {@code ltz.social.outbox.adapter} ile seçilir. */
    @Bean
    @ConditionalOnProperty(name = "ltz.social.outbox.adapter", havingValue = "unavailable", matchIfMissing = true)
    SocialEventPublisher unavailableEventPublisher() {
        return new UnavailableEventPublisher();
    }
}
