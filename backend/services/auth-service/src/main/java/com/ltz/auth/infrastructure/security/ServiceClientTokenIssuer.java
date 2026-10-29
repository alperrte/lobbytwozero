package com.ltz.auth.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.ltz.auth.infrastructure.config.JwtProperties;

/**
 * Servis → Auth yönü: kayıtlı bir servis istemcisine ({@code POST /internal/v1/service-tokens}) Auth'un imzaladığı kısa
 * ömürlü {@code svc+jwt}. Kurallar kodda sabittir:
 * <ul>
 *   <li>{@code typ=svc+jwt}, {@code aud=<tek hedef>} ({@code auth-service} ya da izinli {@code <servis>-service}; yalnız o
 *       serviste geçer, başka servise oynatılamaz), {@code sub=svc:<istemci>}.</li>
 *   <li>Ömür {@link #TTL}; her token için yeni {@code jti}. Rol, oturum ve kullanıcı claim'i taşınmaz; kullanıcı kimliği
 *       gerektiren işlemde kimlik istek gövdesinde ayrıca doğrulanır.</li>
 *   <li>Kapsam, istemcinin kayıtlı kapsamlarından fazlası olamaz (çağıran {@code ServiceClientTokenService} denetler).</li>
 * </ul>
 */
@Component
public class ServiceClientTokenIssuer {

    /** Sözleşme üst sınırı 60 sn (docs-auth/servis-sozlesmeleri.md §3); doğrulayıcı da aynı sınırı uygular. */
    public static final Duration TTL = Duration.ofSeconds(60);

    private final JwtEncoder jwtEncoder;
    private final JwtSigningKey signingKey;
    private final JwtProperties properties;
    private final Clock clock;

    public ServiceClientTokenIssuer(JwtEncoder jwtEncoder, JwtSigningKey signingKey, JwtProperties properties,
                                    Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.signingKey = signingKey;
        this.properties = properties;
        this.clock = clock;
    }

    /** Auth'a ({@code aud=auth-service}) gidecek token. */
    public String issue(String clientId, Set<String> scopes) {
        return issue(clientId, TokenClaims.AUTH_SERVICE_AUDIENCE, scopes);
    }

    /**
     * {@code audience}: token'ın geçeceği tek servis ({@code auth-service} veya {@code <servis>-service}); token yalnız
     * o servisin JWKS doğrulamasında geçer, başka servise oynatılamaz. İzin kontrolü çağıranda ({@code ServiceClientTokenService}).
     */
    public String issue(String clientId, String audience, Set<String> scopes) {
        if (clientId == null || clientId.isBlank() || audience == null || audience.isBlank()
                || scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("Service client token needs a client id, an audience and at least one scope");
        }
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(TokenClaims.SERVICE_SUBJECT_PREFIX + clientId)
                .audience(List.of(audience))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(TTL))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.SCOPE, String.join(" ", new TreeSet<>(scopes)))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(TokenClaims.SERVICE_TOKEN_TYPE)
                .keyId(signingKey.keyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
