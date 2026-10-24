package com.ltz.auth.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.ltz.auth.infrastructure.config.JwtProperties;

/**
 * D4 servis kimliği (docs-auth/servis-sozlesmeleri.md §3): Auth'un bir hedef servise yaptığı iç çağrı için imzaladığı
 * kısa ömürlü token. Kurallar kodda sabittir, yapılandırmayla gevşetilemez:
 * <ul>
 *   <li>{@code typ=svc+jwt}: kullanıcı token'ıyla ({@code at+jwt}) karıştırılamaz; Auth'un kendi doğrulayıcısı ve
 *       kullanıcı token'ı bekleyen her servis bunu reddeder ({@code aud} de {@code ltz-api} değildir).</li>
 *   <li>Tek {@code aud}: yalnız hedef servis ({@code <servis>-service}); başka servise yeniden oynatılamaz.</li>
 *   <li>Tek kapsam ve tek hesap ({@code account_id}): çalınan token başka bir hesap veya işlem için kullanılamaz.</li>
 *   <li>Ömür {@link #TTL}; her çağrı için yeni token ve {@code jti}. Roller, oturum ve kullanıcı claim'i taşınmaz.</li>
 * </ul>
 * İmza, kullanıcı token'larıyla aynı RS256 anahtarıyla atılır; hedef servis JWKS'ten doğrular.
 */
@Component
public class ServiceTokenIssuer {

    /** Sözleşme üst sınırı 60 sn; saat sapması payı için yarısı kullanılır. */
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Pattern AUDIENCE = Pattern.compile(
            "^[a-z0-9][a-z0-9-]{0,31}" + TokenClaims.SERVICE_AUDIENCE_SUFFIX + "$");
    private static final Pattern SCOPE = Pattern.compile("^[a-z]+:[a-z]+$");

    private final JwtEncoder jwtEncoder;
    private final JwtSigningKey signingKey;
    private final JwtProperties properties;
    private final Clock clock;

    public ServiceTokenIssuer(JwtEncoder jwtEncoder, JwtSigningKey signingKey, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.signingKey = signingKey;
        this.properties = properties;
        this.clock = clock;
    }

    /** Servis adını (ör. {@code social}) servis token'ı {@code aud} değerine çevirir ({@code social-service}). */
    public static String audienceFor(String service) {
        return service + TokenClaims.SERVICE_AUDIENCE_SUFFIX;
    }

    public String issue(String audience, String scope, UUID accountId) {
        if (audience == null || !AUDIENCE.matcher(audience).matches()) {
            throw new IllegalArgumentException("Invalid service token audience");
        }
        if (scope == null || !SCOPE.matcher(scope).matches()) {
            throw new IllegalArgumentException("Invalid service token scope");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Service token must be bound to an account");
        }
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(TokenClaims.AUTH_SERVICE_SUBJECT)
                .audience(List.of(audience))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(TTL))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.SCOPE, scope)
                .claim(TokenClaims.ACCOUNT_ID, accountId.toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(TokenClaims.SERVICE_TOKEN_TYPE)
                .keyId(signingKey.keyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
