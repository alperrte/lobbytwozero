package com.ltz.auth.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.ServiceClientProperties;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.ServiceClientTokenIssuer;

/**
 * Servis istemcisinin (ör. admin-service) gizli anahtarını doğrulayıp kısa ömürlü {@code svc+jwt} verir.
 * <ul>
 *   <li>Yanıt, istemcinin var olup olmadığını, devre dışı olup olmadığını veya anahtarın yanlış olduğunu ayırt ettirmez
 *       (tek {@code INVALID_CLIENT}); bilinmeyen istemcide de aynı maliyetli karşılaştırma yapılır.</li>
 *   <li>Gizli anahtar yalnız sabit zamanlı özet karşılaştırmasında kullanılır; hiçbir yere yazılmaz.</li>
 *   <li>Hesap kilidi gibi bir sayaç yoktur: 32+ karakterlik rastgele anahtar tahmin edilemez, sayaç ise iç ağdaki biri
 *       gerçek servisi kilitleyebilirdi. Başarısız denemeler günlüğe istemci kimliğiyle (anahtarsız) yazılır.</li>
 * </ul>
 */
@Service
public class ServiceClientTokenService {

    private static final Logger log = LoggerFactory.getLogger(ServiceClientTokenService.class);
    private static final int CLIENT_ID_LOG_LIMIT = 32;

    private final ServiceClientProperties properties;
    private final ServiceClientTokenIssuer issuer;
    private final SecureTokens secureTokens;
    /** Bilinmeyen/devre dışı istemcide karşılaştırılan rastgele özet (yanıt süresi farkını azaltır). */
    private final byte[] dummyDigest;

    public ServiceClientTokenService(ServiceClientProperties properties, ServiceClientTokenIssuer issuer,
                                     SecureTokens secureTokens) {
        this.properties = properties;
        this.issuer = issuer;
        this.secureTokens = secureTokens;
        this.dummyDigest = digest(secureTokens.newOpaqueToken());
    }

    public IssuedServiceToken issue(String clientId, String clientSecret) {
        Optional<ServiceClientProperties.Client> client = Optional.ofNullable(clientId).map(properties.clients()::get)
                .filter(ServiceClientProperties.Client::enabled);
        byte[] expected = client.map(registered -> digest(registered.secret().strip())).orElse(dummyDigest);
        boolean matches = MessageDigest.isEqual(expected, digest(clientSecret == null ? "" : clientSecret.strip()));
        if (client.isEmpty() || !matches) {
            log.warn("Service client authentication failed clientId={}", safe(clientId));
            throw new AuthException(AuthErrorCode.INVALID_CLIENT);
        }
        Set<String> scopes = client.get().scopes();
        return new IssuedServiceToken(issuer.issue(clientId, scopes), ServiceClientTokenIssuer.TTL,
                String.join(" ", new TreeSet<>(scopes)));
    }

    private byte[] digest(String value) {
        return secureTokens.sha256Hex(value).getBytes(StandardCharsets.US_ASCII);
    }

    /** Günlük enjeksiyonunu önlemek için yalnız beklenen biçimdeki, kısaltılmış kimlik yazılır. */
    private static String safe(String clientId) {
        if (clientId == null) {
            return "-";
        }
        String trimmed = clientId.length() > CLIENT_ID_LOG_LIMIT ? clientId.substring(0, CLIENT_ID_LOG_LIMIT) : clientId;
        return trimmed.matches("^[a-z0-9-]*$") ? trimmed : "?";
    }

    public record IssuedServiceToken(String accessToken, Duration expiresIn, String scope) {

        @Override
        public String toString() {
            return "IssuedServiceToken[accessToken=***, expiresIn=" + expiresIn + ", scope=" + scope + "]";
        }
    }
}
