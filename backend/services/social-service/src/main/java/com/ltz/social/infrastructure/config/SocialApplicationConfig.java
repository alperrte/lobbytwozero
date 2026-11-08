package com.ltz.social.infrastructure.config;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import com.ltz.social.application.port.FriendsVisibilityPort;
import com.ltz.social.application.port.ServiceTokenPort;
import com.ltz.social.application.port.SocialEventPublisher;
import com.ltz.social.infrastructure.client.AuthServiceTokenClient;
import com.ltz.social.infrastructure.client.CommunityEventPublisher;
import com.ltz.social.infrastructure.client.FanOutEventPublisher;
import com.ltz.social.infrastructure.client.UnavailableEventPublisher;
import com.ltz.social.infrastructure.client.UnavailableFriendsVisibility;
import com.ltz.social.infrastructure.client.UserEventPublisher;
import com.ltz.social.infrastructure.client.UserPrivacyClient;

import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({
        AuthProperties.class,
        FriendRequestProperties.class,
        InviteCodeProperties.class,
        OutboxProperties.class,
        RequestLimitProperties.class,
        ServiceClientProperties.class
})
public class SocialApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Teslim yapmayan adaptör; adaptör {@code ltz.social.outbox.adapter} ile seçilir. */
    @Bean
    @ConditionalOnProperty(name = "ltz.social.outbox.adapter", havingValue = "unavailable", matchIfMissing = true)
    SocialEventPublisher unavailableEventPublisher() {
        return new UnavailableEventPublisher();
    }

    /**
     * Giden çağrılar için Auth'tan hedefe bağlı servis token'ı alır (client credentials). Olay yayını ve gizlilik
     * kararı ortak kullanır; gizli anahtar yoksa {@code isConfigured()} false döner ve hiçbir giden çağrı denenmez.
     */
    @Bean
    ServiceTokenPort authServiceTokenClient(RestClient.Builder builder, Clock clock,
                                            ServiceClientProperties properties) {
        return new AuthServiceTokenClient(builder, clock, properties);
    }

    /** Engel olaylarını yalnız community-service'e iletir (Auth: social-service → community-service, events:ingest). */
    @Bean
    @ConditionalOnProperty(name = "ltz.social.outbox.adapter", havingValue = "community")
    SocialEventPublisher communityEventPublisher(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                                 ObjectMapper objectMapper, ServiceClientProperties properties) {
        return new CommunityEventPublisher(builder, serviceTokens, objectMapper, properties);
    }

    /**
     * Olayları türüne göre tüm tüketicilere iletir: engel olayları community ve user'a, arkadaşlık değişikliği user'a
     * (Auth: social-service → community-service ve user-service, events:ingest).
     */
    @Bean
    @ConditionalOnProperty(name = "ltz.social.outbox.adapter", havingValue = "services")
    SocialEventPublisher serviceEventPublisher(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                               ObjectMapper objectMapper, ServiceClientProperties properties) {
        return new FanOutEventPublisher(List.of(
                new CommunityEventPublisher(builder, serviceTokens, objectMapper, properties),
                new UserEventPublisher(builder, serviceTokens, objectMapper, properties)));
    }

    /**
     * Arkadaş listesi görünürlük tercihini user-service'in gizlilik kararı ucundan okur (SOC-12; Auth:
     * social-service → user-service, privacy:read). Tercih okunamazsa başkasının listesi fail-closed gizli kalır.
     */
    @Bean
    @ConditionalOnProperty(name = "ltz.social.privacy.adapter", havingValue = "user")
    FriendsVisibilityPort userPrivacyClient(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                            ServiceClientProperties properties) {
        return new UserPrivacyClient(builder, serviceTokens, properties);
    }

    /** Tercih okumayan adaptör: başkasının arkadaş listesi her zaman gizli (fail-closed). */
    @Bean
    @ConditionalOnProperty(name = "ltz.social.privacy.adapter", havingValue = "unavailable", matchIfMissing = true)
    FriendsVisibilityPort unavailableFriendsVisibility() {
        return new UnavailableFriendsVisibility();
    }
}
