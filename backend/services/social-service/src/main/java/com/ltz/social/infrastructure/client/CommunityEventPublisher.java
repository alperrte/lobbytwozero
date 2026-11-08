package com.ltz.social.infrastructure.client;

import java.util.Set;

import org.springframework.web.client.RestClient;

import com.ltz.social.application.port.ServiceTokenPort;
import com.ltz.social.infrastructure.config.ServiceClientProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * Engel olaylarını ({@code UserBlocked}, {@code UserUnblocked}) community-service'in {@code POST /internal/events}
 * ucuna iletir (Auth: social-service → community-service, {@code events:ingest}). Community bunlarla akış ve yorum
 * görünürlüğünü engel ilişkisine göre süzer. Gönderim kuralları {@link ServiceEventPublisher}'dadır.
 */
public class CommunityEventPublisher extends ServiceEventPublisher {

    static final String COMMUNITY_AUDIENCE = "community-service";
    /** Community'ye iletilen olay türleri. */
    static final Set<String> COMMUNITY_EVENT_TYPES = Set.of("UserBlocked", "UserUnblocked");

    public CommunityEventPublisher(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                   ObjectMapper objectMapper, ServiceClientProperties properties) {
        super(builder, serviceTokens, objectMapper, properties.communityServiceUrl(), COMMUNITY_AUDIENCE,
                COMMUNITY_EVENT_TYPES);
    }
}
