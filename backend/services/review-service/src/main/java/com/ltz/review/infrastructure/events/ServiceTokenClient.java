package com.ltz.review.infrastructure.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Auth Service'in {@code POST /internal/v1/service-tokens} ucundan (client credentials) hedefe bağlı kısa ömürlü servis
 * token'ı alır. Token yalnız geçerlilik süresi dolmadan önce (güvenlik payıyla) bellekte kısa süre yeniden kullanılır.
 * Auth reddederse yeniden deneme yoktur. Hata iletilerine yanıt gövdesi, token veya gizli anahtar yazılmaz.
 */
public class ServiceTokenClient {
    /** Token bitimine bu kadar kala yeniden alınır. */
    static final Duration REFRESH_MARGIN = Duration.ofSeconds(15);
    /** Auth token ömrü üst sınırı (60 sn); daha uzun bildirilse de bundan fazla önbelleğe alınmaz. */
    static final Duration MAX_CACHE = Duration.ofSeconds(60);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String accessToken, String tokenType, long expiresIn, String scope) {}

    private record CachedToken(String value, Instant refreshAt) {}

    /** Token alınamadı (secret yok, Auth reddetti veya erişilemiyor). */
    public static class TokenUnavailableException extends RuntimeException {
        public TokenUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    private final RestClient restClient;
    private final Clock clock;
    private final String clientId;
    private final String clientSecret;
    private final ConcurrentMap<String, CachedToken> cache = new ConcurrentHashMap<>();

    public ServiceTokenClient(RestClient restClient, Clock clock, String clientId, String clientSecret) {
        this.restClient = restClient;
        this.clock = clock;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String tokenFor(String audience) {
        Instant now = clock.instant();
        CachedToken cached = cache.get(audience);
        if (cached != null && now.isBefore(cached.refreshAt())) return cached.value();
        TokenResponse response = request(audience);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank())
            throw new TokenUnavailableException("Auth returned no service token.", null);
        Duration lifetime = Duration.ofSeconds(Math.max(0, response.expiresIn()));
        Duration usable = lifetime.compareTo(MAX_CACHE) > 0 ? MAX_CACHE : lifetime;
        if (usable.compareTo(REFRESH_MARGIN) > 0) cache.put(audience, new CachedToken(response.accessToken(), now.plus(usable).minus(REFRESH_MARGIN)));
        else cache.remove(audience);
        return response.accessToken();
    }

    /** 401/403 sonrası bir sonraki denemede yeni token alınır. */
    public void invalidate(String audience) { cache.remove(audience); }

    private TokenResponse request(String audience) {
        try {
            return restClient.post().uri("/internal/v1/service-tokens").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("clientId", clientId, "clientSecret", clientSecret, "audience", audience))
                    .retrieve().body(TokenResponse.class);
        } catch (RestClientException exception) {
            throw new TokenUnavailableException("Auth refused or could not issue a service token: " + exception.getClass().getSimpleName(), exception);
        }
    }
}
