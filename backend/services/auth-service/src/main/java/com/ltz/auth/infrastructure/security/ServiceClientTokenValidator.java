package com.ltz.auth.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.ltz.auth.infrastructure.config.ServiceClientProperties;

/**
 * Auth'a gelen servis token'ının sözleşme kuralları (imza, {@code iss}, {@code exp}/{@code nbf} ayrıca doğrulanır):
 * <ul>
 *   <li>{@code typ=svc+jwt} ve geçerli bir {@code kid} (kullanıcı token'ı {@code at+jwt} burada geçmez).</li>
 *   <li>{@code aud} <b>yalnız</b> {@code auth-service}: Auth'un başka servislere yolladığı token'lar ({@code aud=<servis>-service})
 *       ve {@code ltz-api} kullanıcı token'ları reddedilir.</li>
 *   <li>{@code sub=svc:<istemci>} ve istemci kayıtlı + etkin; Auth'un kendi kimliği ({@code svc:auth-service}) istemci olamaz.</li>
 *   <li>{@code jti} ve {@code iat} zorunlu; ömür en fazla 60 sn ve bitiş zamanı şimdiden en fazla 60 sn (+ saat sapması) ileride
 *       ({@code iat} çıkarılarak ömür kuralı aşılamaz).</li>
 *   <li>{@code scope}: boş değil ve her kapsam istemcinin kayıtlı kapsamları içinde (kayıttan kapsam çıkarılırsa eski token düşer).</li>
 * </ul>
 */
public class ServiceClientTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final Duration MAX_LIFETIME = Duration.ofSeconds(60);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final ServiceClientProperties clients;
    private final Clock clock;
    private final String signingKeyId;

    public ServiceClientTokenValidator(ServiceClientProperties clients, Clock clock, String signingKeyId) {
        this.clients = clients;
        this.clock = clock;
        this.signingKeyId = signingKeyId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        return isValid(jwt) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Invalid service token", null));
    }

    private boolean isValid(Jwt jwt) {
        Instant now = clock.instant();
        Instant issued = jwt.getIssuedAt();
        Instant expires = jwt.getExpiresAt();
        if (!TokenClaims.SERVICE_TOKEN_TYPE.equals(jwt.getHeaders().get("typ"))
                || !signingKeyId.equals(jwt.getHeaders().get("kid"))
                || !List.of(TokenClaims.AUTH_SERVICE_AUDIENCE).equals(jwt.getAudience())
                || jwt.getId() == null || jwt.getId().isBlank()
                || issued == null || expires == null || jwt.getNotBefore() == null
                || issued.isAfter(now.plus(CLOCK_SKEW))
                || Duration.between(issued, expires).compareTo(MAX_LIFETIME) > 0
                || expires.isAfter(now.plus(MAX_LIFETIME).plus(CLOCK_SKEW))) {
            return false;
        }
        ServiceClientProperties.Client client = registeredClient(jwt.getSubject());
        return client != null && scopesWithin(jwt.getClaimAsString(TokenClaims.SCOPE), client.scopes());
    }

    /** {@code svc:<istemci>} → etkin kayıtlı istemci; yoksa null. */
    private ServiceClientProperties.Client registeredClient(String subject) {
        if (subject == null || !subject.startsWith(TokenClaims.SERVICE_SUBJECT_PREFIX)) {
            return null;
        }
        ServiceClientProperties.Client client = clients.clients().get(subject.substring(TokenClaims.SERVICE_SUBJECT_PREFIX.length()));
        return client != null && client.enabled() ? client : null;
    }

    private static boolean scopesWithin(String scopeClaim, Set<String> allowed) {
        if (scopeClaim == null || scopeClaim.isBlank()) {
            return false;
        }
        Set<String> granted = new HashSet<>(Arrays.asList(scopeClaim.trim().split(" +")));
        return allowed.containsAll(granted);
    }
}
