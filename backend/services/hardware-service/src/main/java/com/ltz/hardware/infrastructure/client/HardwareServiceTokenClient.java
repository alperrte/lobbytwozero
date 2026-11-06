package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.infrastructure.security.HardwareAuthProperties;
import com.ltz.hardware.infrastructure.client.dto.request.HardwareServiceTokenRequest;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Auth client-credentials: her çağrıda yeni token; token/credential loglanmaz veya saklanmaz. */
@Component
@EnableConfigurationProperties(HardwareClientProperties.class)
public class HardwareServiceTokenClient {
    private final HardwareClientProperties properties;
    private final HardwareAuthProperties auth;
    private final HardwareHttpClient http;
    private final ObjectMapper mapper;

    public HardwareServiceTokenClient(HardwareClientProperties properties, HardwareAuthProperties auth,
            HardwareHttpClient http, ObjectMapper mapper) {
        this.properties = properties;
        this.auth = auth;
        this.http = http;
        this.mapper = mapper;
    }

    String userToken() { return issue("user-service", "privacy:read", null); }

    String deviceVerificationToken(UUID accountId) {
        if (accountId == null) throw new IllegalArgumentException("Account binding required");
        return issue("performance-service", "devices:verify", accountId);
    }

    private String issue(String audience, String scope, UUID accountId) {
        if (!properties.configured()) throw new IllegalStateException("Hardware service credential unavailable");
        var request = new HardwareServiceTokenRequest("hardware-service", properties.clientSecret(), audience, accountId);
        JsonNode reply = mapper.readTree(http.post(auth.serviceUrl().resolve("/internal/v1/service-tokens"), request, null));
        String token = text(reply, "accessToken");
        JsonNode expires = reply == null ? null : reply.get("expiresIn");
        boolean valid = "Bearer".equals(text(reply, "tokenType")) && !token.isBlank() && token.length() <= 16384
                && token.chars().noneMatch(Character::isWhitespace)
                && expires != null && expires.isIntegralNumber() && expires.asLong() >= 1 && expires.asLong() <= 60
                && Arrays.asList(text(reply, "scope").split(" +")).contains(scope);
        if (!valid) throw new IllegalArgumentException("Invalid service token response");
        return "Bearer " + token;
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        return value != null && value.isString() ? value.asString() : "";
    }
}
