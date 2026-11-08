package com.ltz.social.infrastructure.client;

import java.util.Set;

import org.springframework.web.client.RestClient;

import com.ltz.social.application.port.ServiceTokenPort;
import com.ltz.social.infrastructure.config.ServiceClientProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * Arkadaşlık ve engel olaylarını user-service'in {@code POST /internal/events} ucuna iletir (Auth: social-service →
 * user-service, {@code events:ingest}). user-service bunlarla gizlilik kararlarında kullandığı arkadaş/engel
 * projeksiyonunu tutar. Yükler user-service'in beklediği alanlardır: {@code FriendshipChanged {userLowId, userHighId,
 * change}}, {@code UserBlocked/UserUnblocked {blockerId, blockedId}}. Gönderim kuralları {@link ServiceEventPublisher}'dadır.
 */
public class UserEventPublisher extends ServiceEventPublisher {

    static final String USER_AUDIENCE = "user-service";
    /** user-service'e iletilen olay türleri (user-service EventType: üretici social-service). */
    static final Set<String> USER_EVENT_TYPES = Set.of("FriendshipChanged", "UserBlocked", "UserUnblocked");

    public UserEventPublisher(RestClient.Builder builder, ServiceTokenPort serviceTokens, ObjectMapper objectMapper,
                              ServiceClientProperties properties) {
        super(builder, serviceTokens, objectMapper, properties.userServiceUrl(), USER_AUDIENCE, USER_EVENT_TYPES);
    }
}
