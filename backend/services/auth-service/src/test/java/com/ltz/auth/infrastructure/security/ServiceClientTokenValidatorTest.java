package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.ltz.auth.infrastructure.config.ServiceClientProperties;

class ServiceClientTokenValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String KEY_ID = "current-kid";

    private final ServiceClientTokenValidator validator = new ServiceClientTokenValidator(
            new ServiceClientProperties(Map.of(
                    "admin", new ServiceClientProperties.Client("x".repeat(32), Set.of("account:read", "account:role:write")),
                    "off", new ServiceClientProperties.Client("", Set.of("account:read")))),
            CLOCK, KEY_ID);

    @Test
    void sozlesmeyeUygunTokenKabulEdilir() {
        assertThat(validate(token -> { })).isTrue();
        assertThat(validate(token -> token.claim("scope", "account:read"))).isTrue();
    }

    @Test
    void tokenTuruVeAnahtarKimligiDenetlenir() {
        assertThat(validate(token -> token.header("typ", "at+jwt"))).isFalse();
        assertThat(validate(token -> token.header("kid", "baska-kid"))).isFalse();
        assertThat(validate(token -> token.headers(headers -> headers.remove("kid")))).isFalse();
    }

    @Test
    void audienceYalnizAuthServiceOlabilir() {
        assertThat(validate(token -> token.audience(List.of("ltz-api")))).isFalse();
        assertThat(validate(token -> token.audience(List.of("auth-service", "game-service")))).isFalse();
        assertThat(validate(token -> token.claims(claims -> claims.remove("aud")))).isFalse();
    }

    @Test
    void konuKayitliVeEtkinIstemciOlmalidir() {
        assertThat(validate(token -> token.subject("svc:auth-service"))).isFalse();
        assertThat(validate(token -> token.subject("svc:unknown"))).isFalse();
        assertThat(validate(token -> token.subject("svc:off"))).as("devre dışı istemci").isFalse();
        assertThat(validate(token -> token.subject("admin"))).isFalse();
        assertThat(validate(token -> token.claims(claims -> claims.remove("sub")))).isFalse();
    }

    @Test
    void kapsamIstemcininKayitliKapsaminiAsamaz() {
        assertThat(validate(token -> token.claim("scope", "account:status:write"))).isFalse();
        assertThat(validate(token -> token.claim("scope", "account:read account:erase"))).isFalse();
        assertThat(validate(token -> token.claim("scope", ""))).isFalse();
        assertThat(validate(token -> token.claims(claims -> claims.remove("scope")))).isFalse();
    }

    @Test
    void omurVeZamanAlanlariZorunluVeSinirli() {
        assertThat(validate(token -> token.claim("exp", NOW.plusSeconds(61)))).as("ömür > 60 sn").isFalse();
        assertThat(validate(token -> token.claim("iat", NOW.minusSeconds(300)).claim("exp", NOW.minusSeconds(240))
                .claim("nbf", NOW.minusSeconds(300)))).as("süresi geçmişte ama ömür kısa: bitiş ileride değil").isTrue();
        assertThat(validate(token -> token.claim("exp", NOW.plusSeconds(400)).claim("iat", NOW.minusSeconds(300))))
                .as("ömür 700 sn").isFalse();
        assertThat(validate(token -> token.claim("iat", NOW.plusSeconds(120)).claim("exp", NOW.plusSeconds(150))))
                .as("gelecekte üretilmiş").isFalse();
        assertThat(validate(token -> token.claims(claims -> claims.remove("iat")))).isFalse();
        assertThat(validate(token -> token.claims(claims -> claims.remove("nbf")))).isFalse();
        assertThat(validate(token -> token.claims(claims -> claims.remove("jti")))).isFalse();
        assertThat(validate(token -> token.claim("jti", " "))).isFalse();
    }

    private boolean validate(Consumer<Jwt.Builder> customize) {
        Jwt.Builder builder = Jwt.withTokenValue("opaque-test-token")
                .header("alg", "RS256").header("typ", "svc+jwt").header("kid", KEY_ID)
                .issuer("ltz-auth").subject("svc:admin").audience(List.of("auth-service"))
                .issuedAt(NOW).notBefore(NOW).expiresAt(NOW.plusSeconds(60))
                .jti(UUID.randomUUID().toString()).claim("scope", "account:read account:role:write");
        customize.accept(builder);
        return !validator.validate(builder.build()).hasErrors();
    }
}
