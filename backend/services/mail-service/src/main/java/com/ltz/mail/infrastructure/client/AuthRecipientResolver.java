package com.ltz.mail.infrastructure.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.port.RecipientResolverPort;
import com.ltz.mail.infrastructure.config.AuthClientProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Alıcı adresini Auth'tan çözer: önce {@code POST /internal/v1/service-tokens} ile {@code account:contact:read} kapsamlı
 * kısa ömürlü (60 sn) token alır (önbelleğe alıp süre dolmadan yeniler), sonra
 * {@code GET /internal/v1/accounts/{id}/contact}. Yanıt gövdesi, adres, token ve gizli anahtar hiçbir ileti/loga
 * yazılmaz. Yönlendirme izlenmez (istemci bu şekilde kurulur).
 *
 * <p>Hata sınıflaması: hesap yok (404) KALICI; Auth erişilemiyor, zaman aşımı, 5xx, token reddi (401) ve yapılandırma
 * hatası (403) GEÇİCİ (kuyrukta bekler, geri çekilmeyle yeniden denenir; yapılandırma düzelince mail kaybolmaz).
 */
@Slf4j
public class AuthRecipientResolver implements RecipientResolverPort {

    static final String TOKEN_UNAVAILABLE = "RESOLVER_TOKEN_UNAVAILABLE";
    static final String RESOLVER_UNAVAILABLE = "RESOLVER_UNAVAILABLE";
    static final String RESOLVER_FORBIDDEN = "RESOLVER_FORBIDDEN";
    static final String RECIPIENT_NOT_FOUND = "RECIPIENT_NOT_FOUND";
    /** Token bitimine bu kadar kala yeniden alınır (saat farkı ve ağ gecikmesi payı). */
    static final Duration REFRESH_MARGIN = Duration.ofSeconds(15);
    /** Auth'un token ömrü üst sınırı; yanıt daha uzun süre bildirse de bundan fazla önbelleğe alınmaz. */
    static final Duration MAX_CACHE = Duration.ofSeconds(60);

    /** Auth token ucu gövdesi; {@code audience} verilmez (varsayılan auth-service). */
    record TokenRequest(String clientId, String clientSecret) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String accessToken, long expiresIn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContactResponse(String status, String email, boolean emailVerified) {
    }

    private record CachedToken(String value, Instant refreshAt) {
    }

    private final RestClient restClient;
    private final Clock clock;
    private final AuthClientProperties properties;
    private volatile CachedToken cached;

    public AuthRecipientResolver(RestClient restClient, Clock clock, AuthClientProperties properties) {
        this.restClient = restClient;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.configured();
    }

    @Override
    public AccountContact resolve(UUID accountId) {
        if (!isConfigured()) {
            throw MailDeliveryException.transientFailure(TOKEN_UNAVAILABLE, null);
        }
        ContactResponse response;
        try {
            String token = token();
            response = restClient.get().uri(properties.contactPath(), accountId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(ContactResponse.class);
        } catch (HttpClientErrorException exception) {
            throw classify(exception.getStatusCode());
        } catch (RestClientException exception) {
            log.warn("Recipient resolution failed cause={}", exception.getClass().getSimpleName());
            throw MailDeliveryException.transientFailure(RESOLVER_UNAVAILABLE, null);
        }
        if (response == null) {
            throw MailDeliveryException.transientFailure(RESOLVER_UNAVAILABLE, null);
        }
        return new AccountContact(response.email(), response.emailVerified(), response.status());
    }

    private MailDeliveryException classify(HttpStatusCode status) {
        if (status.value() == 404) {
            return MailDeliveryException.permanent(RECIPIENT_NOT_FOUND, null);
        }
        if (status.value() == 401) {
            cached = null;
            return MailDeliveryException.transientFailure(TOKEN_UNAVAILABLE, null);
        }
        if (status.value() == 403) {
            log.warn("Auth refused the recipient lookup (check the mail-service client scopes)");
            return MailDeliveryException.transientFailure(RESOLVER_FORBIDDEN, null);
        }
        return MailDeliveryException.transientFailure(RESOLVER_UNAVAILABLE, null);
    }

    private String token() {
        Instant now = clock.instant();
        CachedToken current = cached;
        if (current != null && now.isBefore(current.refreshAt())) {
            return current.value();
        }
        TokenResponse response = requestToken();
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw MailDeliveryException.transientFailure(TOKEN_UNAVAILABLE, null);
        }
        Duration lifetime = Duration.ofSeconds(Math.max(0, response.expiresIn()));
        Duration usable = lifetime.compareTo(MAX_CACHE) > 0 ? MAX_CACHE : lifetime;
        cached = usable.compareTo(REFRESH_MARGIN) > 0
                ? new CachedToken(response.accessToken(), now.plus(usable).minus(REFRESH_MARGIN))
                : null;
        return response.accessToken();
    }

    private TokenResponse requestToken() {
        try {
            return restClient.post().uri(properties.tokenPath()).contentType(MediaType.APPLICATION_JSON)
                    .body(new TokenRequest(properties.clientId(), properties.clientSecret()))
                    .retrieve().body(TokenResponse.class);
        } catch (RestClientException exception) {
            // Yalnız istisna türü loglanır; yanıt gövdesi ve gizli anahtar asla yazılmaz.
            log.warn("Auth refused or could not issue a service token cause={}", exception.getClass().getSimpleName());
            throw MailDeliveryException.transientFailure(TOKEN_UNAVAILABLE, null);
        }
    }
}
