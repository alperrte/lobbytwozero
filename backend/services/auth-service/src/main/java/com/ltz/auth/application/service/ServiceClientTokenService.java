package com.ltz.auth.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.ServiceClientConfig;
import com.ltz.auth.infrastructure.config.ServiceClientProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.ServiceClientTokenIssuer;
import com.ltz.auth.infrastructure.security.TokenClaims;

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
    private final AccountRepository accountRepository;
    /** Bilinmeyen/devre dışı istemcide karşılaştırılan rastgele özet (yanıt süresi farkını azaltır). */
    private final byte[] dummyDigest;

    public ServiceClientTokenService(ServiceClientProperties properties, ServiceClientTokenIssuer issuer,
                                     SecureTokens secureTokens, AccountRepository accountRepository) {
        this.properties = properties;
        this.issuer = issuer;
        this.secureTokens = secureTokens;
        this.accountRepository = accountRepository;
        this.dummyDigest = digest(secureTokens.newOpaqueToken());
    }

    public IssuedServiceToken issue(String clientId, String clientSecret) {
        return issue(clientId, clientSecret, null);
    }

    /**
     * {@code audience} boşsa {@code auth-service}. Kimlik doğrulandıktan sonra hedef istemcinin izin listesinde değilse
     * {@code SERVICE_AUDIENCE_FORBIDDEN}; her hedef için kapsamlar yalnız o hedefe tanımlanan kapsamlardır.
     */
    public IssuedServiceToken issue(String clientId, String clientSecret, String audience) {
        return issue(clientId, clientSecret, audience, null);
    }

    /**
     * Hesaba bağlı kapsamlı hedefte ({@link ServiceClientConfig#ACCOUNT_BOUND_TARGET_SCOPES}) {@code accountId} zorunludur,
     * diğerlerinde verilemez ({@code SERVICE_ACCOUNT_BINDING_INVALID}); hesap ACTIVE değilse ya da yoksa
     * {@code SERVICE_TARGET_ACCOUNT_UNAVAILABLE}. Auth hesabın meşru hedef olduğunu ya da kullanıcı iznini kanıtlamaz:
     * izin kararı hedef servisin (ör. Library → User) işidir; bağ yalnız çalınan token'ı tek hesapla sınırlar.
     */
    public IssuedServiceToken issue(String clientId, String clientSecret, String audience, UUID accountId) {
        String loggedClient = safe(clientId);
        ServiceClientProperties.Client client = authenticate(clientId, clientSecret, loggedClient);
        String target = audience == null || audience.isBlank() ? TokenClaims.AUTH_SERVICE_AUDIENCE : audience;
        String loggedTarget = safe(target);
        Set<String> scopes = TokenClaims.AUTH_SERVICE_AUDIENCE.equals(target)
                ? client.scopes() : client.targets().getOrDefault(target, Set.of());
        if (scopes.isEmpty()) {
            log.warn("Service client audience not allowed clientId={} audience={}", loggedClient, loggedTarget);
            throw new AuthException(AuthErrorCode.SERVICE_AUDIENCE_FORBIDDEN);
        }
        requireAccountBinding(scopes, accountId, loggedClient, loggedTarget);
        return new IssuedServiceToken(issuer.issue(clientId, target, scopes, accountId), ServiceClientTokenIssuer.TTL,
                String.join(" ", new TreeSet<>(scopes)));
    }

    /** Yanıt, istemcinin yokluğunu, devre dışılığını ve yanlış anahtarı ayırt ettirmez (tek {@code INVALID_CLIENT}). */
    private ServiceClientProperties.Client authenticate(String clientId, String clientSecret, String loggedClient) {
        Optional<ServiceClientProperties.Client> client = Optional.ofNullable(clientId).map(properties.clients()::get)
                .filter(ServiceClientProperties.Client::enabled);
        byte[] expected = client.map(registered -> digest(registered.secret().strip())).orElse(dummyDigest);
        boolean matches = MessageDigest.isEqual(expected, digest(clientSecret == null ? "" : clientSecret.strip()));
        if (client.isEmpty() || !matches) {
            log.warn("Service client authentication failed clientId={}", loggedClient);
            throw new AuthException(AuthErrorCode.INVALID_CLIENT);
        }
        return client.get();
    }

    /** Hesaba bağlı kapsamda hesap zorunlu ve ACTIVE; bağsız kapsamda hesap verilemez. */
    private void requireAccountBinding(Set<String> scopes, UUID accountId, String loggedClient, String loggedTarget) {
        boolean accountBound = ServiceClientConfig.isAccountBound(scopes);
        if (accountBound != (accountId != null)) {
            log.warn("Service client account binding invalid clientId={} audience={}", loggedClient, loggedTarget);
            throw new AuthException(AuthErrorCode.SERVICE_ACCOUNT_BINDING_INVALID);
        }
        if (accountBound && accountRepository.findById(accountId).filter(Account::isActive).isEmpty()) {
            log.warn("Service client target account unavailable clientId={} audience={}", loggedClient, loggedTarget);
            throw new AuthException(AuthErrorCode.SERVICE_TARGET_ACCOUNT_UNAVAILABLE);
        }
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
