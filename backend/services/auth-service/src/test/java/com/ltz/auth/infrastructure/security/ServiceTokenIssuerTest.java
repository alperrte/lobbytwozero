package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import com.ltz.auth.ServiceTokenContract;
import com.ltz.auth.TestServiceTokens;

class ServiceTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SOCIAL = "social-service";
    private static final String ERASE = TokenClaims.ACCOUNT_ERASE_SCOPE;

    private final ServiceTokenIssuer issuer = TestServiceTokens.issuer(CLOCK);
    private final JwtSigningKey key = TestServiceTokens.signingKey();

    @Test
    void tokenHedefServisinZorunluDenetimleriniGecer() {
        UUID accountId = UUID.randomUUID();

        Jwt jwt = verify(issuer.issue(SOCIAL, ERASE, accountId), SOCIAL, accountId, CLOCK);

        assertThat(jwt.getHeaders()).containsEntry("typ", TokenClaims.SERVICE_TOKEN_TYPE)
                .containsEntry("kid", key.keyId()).containsEntry("alg", "RS256");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(TestServiceTokens.ISSUER);
        assertThat(jwt.getSubject()).isEqualTo("svc:auth-service");
        assertThat(jwt.getAudience()).containsExactly(SOCIAL);
        assertThat(jwt.getClaimAsString(TokenClaims.SCOPE)).isEqualTo("account:erase");
        assertThat(jwt.getClaimAsString(TokenClaims.ACCOUNT_ID)).isEqualTo(accountId.toString());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(30));
        assertThat(jwt.getClaims()).doesNotContainKeys(TokenClaims.ROLES, TokenClaims.SESSION_ID,
                TokenClaims.TOKEN_VERSION, TokenClaims.AUTH_METHODS);
    }

    @Test
    void baskaServiseVeyaBaskaHesabaKullanilamaz() {
        UUID accountId = UUID.randomUUID();
        UUID otherAccount = UUID.randomUUID();
        String token = issuer.issue(SOCIAL, ERASE, accountId);

        assertThatThrownBy(() -> verify(token, "user-service", accountId, CLOCK)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> verify(token, SOCIAL, otherAccount, CLOCK)).isInstanceOf(JwtException.class);
    }

    @Test
    void suresiDolanTokenSaatSapmasiPayindanSonraReddedilir() {
        UUID accountId = UUID.randomUUID();
        String token = issuer.issue(SOCIAL, ERASE, accountId);
        Clock afterExpiry = Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC);

        assertThatThrownBy(() -> verify(token, SOCIAL, accountId, afterExpiry)).isInstanceOf(JwtException.class);
    }

    @Test
    void herCagriYeniJtiTasir() {
        UUID accountId = UUID.randomUUID();

        Jwt first = verify(issuer.issue(SOCIAL, ERASE, accountId), SOCIAL, accountId, CLOCK);
        Jwt second = verify(issuer.issue(SOCIAL, ERASE, accountId), SOCIAL, accountId, CLOCK);

        assertThat(first.getId()).isNotBlank().isNotEqualTo(second.getId());
    }

    @Test
    void gecersizHedefKapsamVeyaHesapIcinTokenUretilmez() {
        UUID accountId = UUID.randomUUID();

        for (String audience : new String[]{"social", "ltz-api", "Social-service", "-service", "--service", "a/b-service", ""}) {
            assertThatThrownBy(() -> issuer.issue(audience, ERASE, accountId)).as(audience)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> issuer.issue(null, ERASE, accountId)).isInstanceOf(IllegalArgumentException.class);
        for (String scope : new String[]{"account:erase admin:all", "ACCOUNT:ERASE", "account", ""}) {
            assertThatThrownBy(() -> issuer.issue(SOCIAL, scope, accountId)).as(scope)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> issuer.issue(SOCIAL, null, accountId)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issuer.issue(SOCIAL, ERASE, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void servisAdiAudDegerineCevrilir() {
        assertThat(ServiceTokenIssuer.audienceFor("social")).isEqualTo(SOCIAL);
        assertThat(ServiceTokenIssuer.audienceFor("game")).isEqualTo("game-service");
    }

    private Jwt verify(String token, String audience, UUID accountId, Clock clock) {
        return ServiceTokenContract.verify(token, key.publicKey(), TestServiceTokens.ISSUER, audience, accountId, clock);
    }
}
