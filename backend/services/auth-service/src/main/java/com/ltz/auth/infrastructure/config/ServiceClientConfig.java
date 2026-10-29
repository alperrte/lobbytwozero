package com.ltz.auth.infrastructure.config;

import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.ltz.auth.infrastructure.security.TokenClaims;

/**
 * Servis istemcilerini açılışta denetler: kimlik biçimi, en az 32 karakterlik gizli anahtar ve bilinen kapsamlar.
 * Zayıf veya tanımsız kapsamlı bir istemci servisi açtırmaz (sessizce gevşek çalışmaktansa durur). Hata mesajları
 * gizli anahtarı içermez.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServiceClientProperties.class)
public class ServiceClientConfig {

    /** Auth'un iç uçlarının tanıdığı kapsamlar; istemciye bunların dışında kapsam verilemez. */
    public static final Set<String> ALLOWED_SCOPES = Set.of(
            TokenClaims.ACCOUNT_READ_SCOPE, TokenClaims.ACCOUNT_STATUS_WRITE_SCOPE, TokenClaims.ACCOUNT_ROLE_WRITE_SCOPE);
    /** Başka servislere ({@code aud=<servis>-service}) verilebilen kapsamlar; hedef servis token'ı kendi JWKS doğrulamasıyla kabul eder. */
    public static final Set<String> ALLOWED_TARGET_SCOPES = Set.of(
            TokenClaims.EVENTS_INGEST_SCOPE, TokenClaims.BLOCKS_CHECK_SCOPE);
    static final int MIN_SECRET_LENGTH = 32;
    private static final Pattern CLIENT_ID = Pattern.compile("^[a-z0-9-]{1,32}$");
    private static final Pattern TARGET_AUDIENCE = Pattern.compile("^[a-z0-9-]{1,24}" + TokenClaims.SERVICE_AUDIENCE_SUFFIX + "$");

    ServiceClientConfig(ServiceClientProperties properties) {
        properties.clients().forEach(ServiceClientConfig::validate);
    }

    private static void validate(String clientId, ServiceClientProperties.Client client) {
        String property = "ltz.auth.service-clients.clients." + clientId;
        if (!CLIENT_ID.matcher(clientId).matches()) {
            throw new IllegalStateException("Service client id must match " + CLIENT_ID.pattern());
        }
        if (client == null || !client.enabled()) {
            return;
        }
        if (client.secret().strip().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(property + ".secret must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        if (client.scopes().isEmpty() && client.targets().isEmpty()) {
            throw new IllegalStateException(property + " needs scopes or targets");
        }
        if (!ALLOWED_SCOPES.containsAll(client.scopes())) {
            throw new IllegalStateException(property + ".scopes must be a subset of " + ALLOWED_SCOPES);
        }
        client.targets().forEach((audience, granted) -> validateTarget(property, audience, granted));
    }

    private static void validateTarget(String property, String audience, Set<String> granted) {
        if (!TARGET_AUDIENCE.matcher(audience).matches() || TokenClaims.AUTH_SERVICE_AUDIENCE.equals(audience)) {
            throw new IllegalStateException(property + ".targets key must be another service audience like community-service");
        }
        if (granted.isEmpty() || !ALLOWED_TARGET_SCOPES.containsAll(granted)) {
            throw new IllegalStateException(
                    property + ".targets." + audience + " must be a non-empty subset of " + ALLOWED_TARGET_SCOPES);
        }
    }
}
