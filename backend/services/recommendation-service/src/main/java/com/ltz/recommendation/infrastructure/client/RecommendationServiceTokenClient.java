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
        if (secret.isBlank()) throw RecommendationHttpClient.unavailable();
        var reply = http.post(auth.serviceUrl().resolve("/internal/v1/service-tokens"),
                new TokenRequest("recommendation-service", secret, "library-service", accountId));
        var token = reply.path("accessToken");
        var expires = reply.path("expiresIn");
        if (!"Bearer".equals(reply.path("tokenType").asString()) || !token.isString()
                || token.asString().isBlank() || token.asString().length() > 16384
                || token.asString().chars().anyMatch(Character::isWhitespace)
                || !expires.isIntegralNumber() || expires.asLong() < 1 || expires.asLong() > 60
                || !Arrays.asList(reply.path("scope").asString().split(" +")).contains("signals:read")) {
            throw RecommendationHttpClient.unavailable();
        }
        return "Bearer " + token.asString();
    }

    private record TokenRequest(String clientId, String clientSecret, String audience, UUID accountId) {
        @Override public String toString() { return "TokenRequest[credentials=REDACTED]"; }
    }
}
