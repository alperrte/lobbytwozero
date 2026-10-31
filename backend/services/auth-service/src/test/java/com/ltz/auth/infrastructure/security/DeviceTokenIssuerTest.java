package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.ltz.auth.TestServiceTokens;
import com.ltz.auth.infrastructure.config.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Hedef düz String olarak geldiği için (Hardware'in derleme kümesi yeni Auth tipine izin vermez), sınır üreticinin
 * kendisinde zorlanır: yalnız tanımlı (audience → kapsam) çiftleri imzalanır.
 */
class DeviceTokenIssuerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC);

    private final DeviceTokenIssuer issuer = new DeviceTokenIssuer(
            new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(TestServiceTokens.signingKey().jwk()))),
            TestServiceTokens.signingKey(),
            new JwtProperties(TestServiceTokens.ISSUER, "ltz-api", java.time.Duration.ofMinutes(5), "unused", null), CLOCK);

    @Test
    void tanimliIkiCiftImzalanirVarsayilanHardwaredir() throws Exception {
        UUID device = UUID.randomUUID();
        UUID account = UUID.randomUUID();

        assertThat(claims(issuer.issue(device, account), "aud")).isEqualTo("[hardware-service]");
        assertThat(claims(issuer.issue(device, account), "scope")).isEqualTo("hardware:sync:write");
        assertThat(claims(issuer.issue(device, account, "performance-service", "performance:sync:write"), "aud"))
                .isEqualTo("[performance-service]");
        assertThat(claims(issuer.issue(device, account, "performance-service", "performance:sync:write"), "scope"))
                .isEqualTo("performance:sync:write");
    }

    @Test
    void tanimsizAudienceKapsamVeKarisikCiftlerImzalanmaz() {
        UUID device = UUID.randomUUID();
        UUID account = UUID.randomUUID();

        // Hesap silme kapsamı hiçbir hedefle cihaz token'ına yazılamaz (okuma/yazma ile silme yetkisi ayrıdır).
        assertThatThrownBy(() -> issuer.issue(device, account, "hardware-service", TokenClaims.ACCOUNT_ERASE_SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
        // Doğru kapsam, yanlış hedef; doğru hedef, başka hedefin kapsamı.
        assertThatThrownBy(() -> issuer.issue(device, account, "hardware-service", "performance:sync:write"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issuer.issue(device, account, "performance-service", "hardware:sync:write"))
                .isInstanceOf(IllegalArgumentException.class);
        // Tanımsız hedefler ve boş/null değerler.
        assertThatThrownBy(() -> issuer.issue(device, account, "admin-service", "hardware:sync:write"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issuer.issue(device, account, "ltz-api", "hardware:sync:write"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issuer.issue(device, account, null, "hardware:sync:write"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issuer.issue(device, account, "hardware-service", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issuer.issue(device, account, "hardware-service", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String claims(String token, String name) throws Exception {
        return String.valueOf(SignedJWT.parse(token).getJWTClaimsSet().getClaim(name));
    }
}
