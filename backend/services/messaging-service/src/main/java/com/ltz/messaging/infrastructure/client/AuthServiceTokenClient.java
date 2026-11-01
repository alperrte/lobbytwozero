package com.ltz.messaging.infrastructure.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ltz.messaging.application.port.ServiceTokenPort;
import com.ltz.messaging.application.port.ServiceTokenUnavailableException;
import com.ltz.messaging.infrastructure.config.ServiceClientProperties;

/**
 * Auth Service'in {@code POST /internal/v1/service-tokens} ucundan (client credentials) hedefe bağlı servis token'ı
 * alır. Token en fazla 60 sn yaşar; aynı hedef için yalnız bitimine {@link #REFRESH_MARGIN}'den fazla varken yeniden
 * kullanılır. Gizli anahtar yoksa Auth hiç çağrılmaz (fail-closed). İletilere yanıt gövdesi, token ya da gizli anahtar
 * yazılmaz.
 */
public class AuthServiceTokenClient implements ServiceTokenPort {

    /** Token bitimine bu kadar kala yeniden alınır (saat farkı ve ağ gecikmesi payı). */
    static final Duration REFRESH_MARGIN = Duration.ofSeconds(15);
    /** Auth'un servis token'ı ömür üst sınırı; yanıt daha uzun süre bildirse de bundan fazla tutulmaz. */
    static final Duration MAX_LIFETIME = Duration.ofSeconds(60);

    /** Auth'un yanıtı; yeni alanlar eklenirse kırılmasın diye bilinmeyen alanlar yok sayılır. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String accessToken, long expiresIn) {
    }

    record TokenRequest(String clientId, String clientSecret, String audience) {
    }

    private record Issued(String token, Instant reuseUntil) {
    }

    private final RestClient restClient;
    private final ServiceClientProperties properties;
    private final Clock clock;
    private final Map<String, Issued> issued = new ConcurrentHashMap<>();

    public AuthServiceTokenClient(RestClient.Builder builder, Clock clock, ServiceClientProperties properties) {
        this.restClient = builder.clone().baseUrl(properties.authServiceUrl()).build();
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String tokenFor(String audience) {
        if (!properties.hasClientSecret()) {
            throw new ServiceTokenUnavailableException("The Auth client secret is not configured.");
        }
        Instant now = clock.instant();
        Issued cached = issued.get(audience);
        if (cached != null && now.isBefore(cached.reuseUntil())) {
            return cached.token();
        }
        TokenResponse response = fetch(audience);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new ServiceTokenUnavailableException("Auth returned no service token.");
        }
        Duration lifetime = Duration.ofSeconds(Math.clamp(response.expiresIn(), 0, MAX_LIFETIME.toSeconds()));
        if (lifetime.compareTo(REFRESH_MARGIN) > 0) {
            issued.put(audience, new Issued(response.accessToken(), now.plus(lifetime).minus(REFRESH_MARGIN)));
        } else {
            issued.remove(audience);
        }
        return response.accessToken();
    }

    private TokenResponse fetch(String audience) {
        try {
            return restClient.post()
                    .uri(properties.tokenPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TokenRequest(properties.clientId(), properties.clientSecret(), audience))
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException exception) {
            // İleti yalnız istisna türünü taşır (yanıt gövdesi yok); neden zincirde kalır.
            throw new ServiceTokenUnavailableException("Auth could not issue a service token: "
                    + exception.getClass().getSimpleName(), exception);
        }
    }
}
