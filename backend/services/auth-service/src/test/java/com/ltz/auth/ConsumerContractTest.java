package com.ltz.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import com.ltz.auth.api.controller.JwksController;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.AuthSession;
import com.ltz.auth.domain.entity.Role;
import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.infrastructure.config.JwtProperties;
import com.ltz.auth.infrastructure.security.AccessTokenIssuer;
import com.ltz.auth.infrastructure.security.JwtSigningKey;
import com.ltz.auth.infrastructure.security.ServiceTokenIssuer;
import com.ltz.auth.infrastructure.security.TokenClaims;

/**
 * Auth'un başka servislerin testlerince KULLANILAN imzalarını kilitler. Game, Hardware, Library ve Review'ın sözleşme
 * testleri (ProductionAuthSupport / ProductionServiceTokenSupport) bu sınıfları gerçek Auth kaynağından derleyip
 * yansımayla çağırır. Bir imza değişirse onların testleri sessizce kırılır; bu test aynı değişikliği Auth'un kendi
 * derlemesinde yakalar. Bir imzayı BİLEREK değiştirmek gerekiyorsa önce o servislerin sahiplerine haber verilir ve bu test
 * ile {@code docs-auth/README.md} "Başkalarının bağlı olduğu Auth imzaları" bölümü birlikte güncellenir.
 */
class ConsumerContractTest {

    @Test
    void jwtPropertiesDortParametreliYapiciKorunur() throws Exception {
        JwtProperties properties = JwtProperties.class
                .getConstructor(String.class, String.class, Duration.class, String.class)
                .newInstance("ltz-auth", "ltz-api", Duration.ofMinutes(1), "key");

        assertThat(properties.additionalPublicKeys()).isEmpty();
    }

    @Test
    void imzaAnahtariVeJwksYuzeyiKorunur() throws Exception {
        assertThat(Modifier.isStatic(JwtSigningKey.class.getMethod("fromBase64Pkcs8", String.class).getModifiers())).isTrue();
        assertThatCode(() -> {
            JwtSigningKey.class.getMethod("jwk");
            JwtSigningKey.class.getMethod("publicJwkSet");
            JwksController.class.getConstructor(JwtSigningKey.class);
            JwksController.class.getMethod("jwks");
        }).doesNotThrowAnyException();
    }

    @Test
    void tokenUreticileriKorunur() throws Exception {
        assertThatCode(() -> {
            AccessTokenIssuer.class.getConstructor(JwtEncoder.class, JwtSigningKey.class, JwtProperties.class);
            AccessTokenIssuer.class.getMethod("issue", Account.class, AuthSession.class, Instant.class);
            AccessTokenIssuer.IssuedAccessToken.class.getMethod("value");
            ServiceTokenIssuer.class.getConstructor(JwtEncoder.class, JwtSigningKey.class, JwtProperties.class, Clock.class);
            ServiceTokenIssuer.class.getMethod("issue", String.class, String.class, UUID.class);
        }).doesNotThrowAnyException();
    }

    @Test
    void hesapVeOturumFabrikalariKorunur() throws Exception {
        assertThatCode(() -> {
            Account.class.getMethod("registerVerifiedExternal", String.class, String.class, Role.class, Instant.class);
            Account.class.getMethod("grantRole", Role.class, Instant.class);
            Account.class.getMethod("getTokenVersion");
            AuthSession.class.getMethod("open", UUID.class, long.class, List.class, Instant.class, Instant.class);
            AuthMethod.valueOf("PWD");
        }).doesNotThrowAnyException();
    }

    @Test
    void tokenSozlesmesiSabitleriDegismez() {
        assertThat(TokenClaims.ACCESS_TOKEN_TYPE).isEqualTo("at+jwt");
        assertThat(TokenClaims.SERVICE_TOKEN_TYPE).isEqualTo("svc+jwt");
        assertThat(TokenClaims.AUTH_SERVICE_SUBJECT).isEqualTo("svc:auth-service");
        assertThat(TokenClaims.ACCOUNT_ERASE_SCOPE).isEqualTo("account:erase");
        assertThat(TokenClaims.ACCOUNT_ID).isEqualTo("account_id");
        assertThat(TokenClaims.ROLES).isEqualTo("roles");
    }
}
