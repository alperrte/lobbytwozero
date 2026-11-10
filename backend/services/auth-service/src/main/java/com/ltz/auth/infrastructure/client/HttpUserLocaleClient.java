package com.ltz.auth.infrastructure.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.application.port.UserLocalePort;
import com.ltz.auth.infrastructure.config.DeletionProperties;
import com.ltz.auth.infrastructure.security.ServiceTokenIssuer;
import com.ltz.auth.infrastructure.security.TokenClaims;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth → User Service (MAIL-AUTH-REQ-02): {@code GET <USER_SERVICE_URL>/internal/v1/users/{userId}/locale}, her
 * çağrıda yeni servis token'ıyla ({@code aud=user-service}, {@code scope=preferences:locale:read},
 * {@code account_id}). Yanıt yalnız {@code {"locale":"tr"|"en"}} taşır.
 * <p>
 * En iyi çaba: kısa zaman aşımı ({@link #MAX_TIMEOUT}), her hata/404/geçersiz değer "bilinmiyor" olur ve mail
 * iki dilli gider. Yalnız başarılı değer, hesap başına {@link #CACHE_TTL} önbelleğe alınır (profil sonradan oluşabilir).
 * Dil, adres ve yanıt gövdesi loglanmaz; yalnız hata sınıfı ve durum kodu debug düzeyinde yazılır.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ltz.auth.mail.adapter", havingValue = "mail-service")
public class HttpUserLocaleClient implements UserLocalePort {

    static final String USER_SERVICE = "user";
    static final Duration MAX_TIMEOUT = Duration.ofSeconds(2);
    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int MAX_CACHE_ENTRIES = 10_000;
    private static final String[] PATH_SEGMENTS = {"internal", "v1", "users"};
    private static final int MAX_BODY = 1024;
    /** Yanıt yalnız {"locale":"tr"|"en"} olabilir; başka değer ("de" vb.) ve ek alan "bilinmiyor" sayılır. */
    private static final Pattern LOCALE_BODY =
            Pattern.compile("^\\s*\\{\\s*\"locale\"\\s*:\\s*\"(tr|en)\"\\s*}\\s*$");
    private static final String BEARER = "Bearer ";

    private record Cached(String locale, Instant expiresAt) {
    }

    private final HttpClient httpClient;
    private final RestClient restClient;
    private final ServiceTokenIssuer serviceTokens;
    private final Clock clock;
    private final URI baseUrl;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public HttpUserLocaleClient(DeletionProperties properties, ServiceTokenIssuer serviceTokens,
                                Clock clock) {
        this.baseUrl = properties.serviceUrls().get(USER_SERVICE);
        Duration timeout = properties.httpTimeout().compareTo(MAX_TIMEOUT) < 0 ? properties.httpTimeout() : MAX_TIMEOUT;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.serviceTokens = serviceTokens;
        this.clock = clock;
    }

    @PreDestroy
    void close() {
        httpClient.close();
    }

    @Override
    public Optional<String> localeOf(UUID accountId) {
        if (accountId == null || baseUrl == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Cached cached = cache.get(accountId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return Optional.of(cached.locale());
        }
        Optional<String> fetched = fetch(accountId);
        fetched.ifPresent(locale -> remember(accountId, locale, now));
        return fetched;
    }

    private Optional<String> fetch(UUID accountId) {
        try {
            String token = serviceTokens.issue(ServiceTokenIssuer.audienceFor(USER_SERVICE),
                    TokenClaims.PREFERENCES_LOCALE_READ_SCOPE, accountId);
            String body = restClient.get()
                    .uri(uri(accountId))
                    .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                    .exchangeForRequiredValue((request, response) ->
                            response.getStatusCode().value() == 200
                                    ? new String(response.getBody().readNBytes(MAX_BODY), StandardCharsets.UTF_8)
                                    : null);
            if (body == null) {
                log.debug("User locale unavailable (not 200)");
                return Optional.empty();
            }
            Matcher matcher = LOCALE_BODY.matcher(body);
            return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
        } catch (Exception e) {
            log.debug("User locale unavailable cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void remember(UUID accountId, String locale, Instant now) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                return;
            }
        }
        cache.put(accountId, new Cached(locale, now.plus(CACHE_TTL)));
    }

    private URI uri(UUID accountId) {
        return UriComponentsBuilder.newInstance()
                .scheme(baseUrl.getScheme())
                .host(baseUrl.getHost())
                .port(baseUrl.getPort())
                .pathSegment(PATH_SEGMENTS)
                .pathSegment(accountId.toString(), "locale")
                .build()
                .encode()
                .toUri();
    }
}
