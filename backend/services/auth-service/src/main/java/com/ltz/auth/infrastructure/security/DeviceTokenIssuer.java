package com.ltz.auth.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.ltz.auth.infrastructure.config.JwtProperties;

/**
 * Masaüstü cihaz token'ı. Kurallar kodda sabittir (cihaz kimliği tasarımı, {@code docs-auth/entegrasyon}):
 * <ul>
 *   <li>{@code typ=dev+jwt}: kullanıcı ({@code at+jwt}) ve servis ({@code svc+jwt}) token'ının yerine geçemez.</li>
 *   <li>{@code aud=hardware-service}: yalnız Hardware'de geçer, Auth'ta ve başka serviste geçmez.</li>
 *   <li>{@code sub=<deviceId>}, {@code account_id=<hesap>}, {@code scope=hardware:sync:write}: başka kapsam yoktur.</li>
 *   <li>Ömür {@link #TTL}; her token için yeni {@code jti}. Rol, oturum ve kullanıcı claim'i taşınmaz.</li>
 * </ul>
 */
@Component
public class DeviceTokenIssuer {

    /** Servis token'larıyla aynı kısa ömür; iptal en geç bu sürede etkili olur. */
    public static final Duration TTL = Duration.ofSeconds(60);

    private final JwtEncoder jwtEncoder;
    private final JwtSigningKey signingKey;
    private final JwtProperties properties;
    private final Clock clock;

    public DeviceTokenIssuer(JwtEncoder jwtEncoder, JwtSigningKey signingKey, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.signingKey = signingKey;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(UUID deviceId, UUID accountId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(deviceId.toString())
                .audience(List.of(TokenClaims.HARDWARE_SERVICE_AUDIENCE))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(TTL))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.SCOPE, TokenClaims.HARDWARE_SYNC_WRITE_SCOPE)
                .claim(TokenClaims.ACCOUNT_ID, accountId.toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(TokenClaims.DEVICE_TOKEN_TYPE)
                .keyId(signingKey.keyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
