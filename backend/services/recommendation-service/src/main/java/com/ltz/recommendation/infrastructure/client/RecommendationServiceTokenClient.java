package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.infrastructure.security.RecommendationAuthProperties;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecommendationServiceTokenClient {
    private final RecommendationHttpClient http;
    private final RecommendationAuthProperties auth;
    private final String secret;
    public RecommendationServiceTokenClient(RecommendationHttpClient http, RecommendationAuthProperties auth,
            @Value("${ltz.recommendation.client-secret:}") String secret) {
        this.http = http;
        this.auth = auth;
        this.secret = secret;
    }

    public String libraryToken(UUID accountId) {
        return issue("library-service", "signals:read", accountId);
    }

    public String candidatesToken(UUID accountId) {
        return issue("user-service", "candidates:read", accountId);
    }

    public String gameMetadataToken(UUID accountId) {
        return issue("game-service", "game-metadata:read", accountId);
    }

    public String performanceToken(UUID accountId) {
        return issue("performance-service", "reference:read", accountId);
    }

    private String issue(String audience, String scope, UUID accountId) {
        if (accountId == null) throw new IllegalArgumentException("Account binding required");
        if (secret.isBlank()) throw RecommendationHttpClient.unavailable();
        var reply = http.post(auth.serviceUrl().resolve("/internal/v1/service-tokens"),
                new TokenRequest("recommendation-service", secret, audience, accountId));
        return readToken(reply, scope);
    }

    private static String readToken(tools.jackson.databind.JsonNode reply, String scope) {
        var token = reply.path("accessToken");
        var expires = reply.path("expiresIn");
        if (!"Bearer".equals(text(reply.path("tokenType"))) || !token.isString()
                || token.asString().isBlank() || token.asString().length() > 16384
                || token.asString().chars().anyMatch(Character::isWhitespace)
                || !expires.isIntegralNumber() || !expires.canConvertToLong() || expires.asLong() < 1 || expires.asLong() > 60
                || !Arrays.asList(text(reply.path("scope")).split(" +")).contains(scope)) {
            throw RecommendationHttpClient.unavailable();
        }
        return "Bearer " + token.asString();
    }

    private static String text(tools.jackson.databind.JsonNode value) { return value.isString() ? value.asString() : ""; }

    private record TokenRequest(String clientId, String clientSecret, String audience, UUID accountId) {
        @Override public String toString() { return "TokenRequest[credentials=REDACTED]"; }
    }
}
