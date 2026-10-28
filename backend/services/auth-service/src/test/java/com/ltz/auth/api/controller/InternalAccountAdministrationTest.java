package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.infrastructure.security.JwtSigningKey;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.http.Cookie;

/**
 * B4 altyapısı: admin-service → Auth servis kimliği ({@code POST /internal/v1/service-tokens}) ve iç hesap yönetimi uçları.
 * Sertleştirme: yanlış tür/aud/sub/kapsam/ömür, kullanıcı token'ı ve cookie ile erişim, yönetici (actor) doğrulaması.
 */
class InternalAccountAdministrationTest extends IntegrationTestSupport {

    private static final String REASON = "Servis kimlikli yönetim (test)";
    private static final String ALL_SCOPES = "account:read account:role:write account:status:write";

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtSigningKey signingKey;

    // ---- Token ucu ----

    @Test
    void tokenUcuSozlesmeyeUygunKisaOmurluTokenVerir() throws Exception {
        String body = tokenRequest("admin-service", ADMIN_CLIENT_SECRET).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(60))
                .andExpect(jsonPath("$.scope").value(ALL_SCOPES))
                .andReturn().getResponse().getContentAsString();

        String accessToken = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
        SignedJWT jwt = SignedJWT.parse(accessToken);
        assertThat(jwt.getHeader().getType().getType()).isEqualTo("svc+jwt");
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(signingKey.keyId());
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("auth-service");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("svc:admin-service");
        assertThat(jwt.getJWTClaimsSet().getJWTID()).isNotBlank();
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().getTime() - jwt.getJWTClaimsSet().getIssueTime().getTime())
                .isEqualTo(60_000L);
        assertThat(jwt.getJWTClaimsSet().getClaims()).doesNotContainKeys("roles", "sid", "ver", "amr", "account_id");
    }

    @Test
    void yanlisBilinmeyenVeBosAnahtarAyniYanitiAlir() throws Exception {
        String wrong = tokenRequest("admin-service", "yanlis-anahtar-yanlis-anahtar-yanlis-anahtar").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT")).andReturn().getResponse().getContentAsString();
        String unknown = tokenRequest("nobody", ADMIN_CLIENT_SECRET).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(wrong);
        tokenRequest("admin-service", "").andExpect(status().isBadRequest());
        tokenRequest("Admin;DROP", ADMIN_CLIENT_SECRET).andExpect(status().isBadRequest());
    }

    // ---- Kimlik doğrulama sertliği ----

    @Test
    void kimliksizKullaniciTokeniVeCookieIleIcUclaraGirilemez() throws Exception {
        UUID target = newAccount();
        String userBearer = bearerFor(newAdmin());
        String path = "/internal/v1/accounts/" + target;

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        // Kullanıcı access token'ı (at+jwt, aud=ltz-api) servis ucunda geçmez; ADMIN rolü de olsa.
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, userBearer)).andExpect(status().isUnauthorized());
        // Cookie iç uçlarda hiç okunmaz.
        mockMvc.perform(get(path).cookie(new Cookie(ACCESS_COOKIE, userBearer.substring("Bearer ".length()))))
                .andExpect(status().isUnauthorized());
        // İç uç kullanıcı uçlarına da servis token'ıyla girilemez.
        String serviceBearer = serviceBearer();
        mockMvc.perform(get(AuthApiPaths.ADMIN_ROLES).header(HttpHeaders.AUTHORIZATION, serviceBearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sozlesmeyiBozanTokenlarReddedilir() throws Exception {
        String path = "/internal/v1/accounts/" + newAccount();

        List<String> rejected = List.of(
                craft("svc+jwt", signingKey.keyId(), claims -> claims.audience(List.of("ltz-api"))),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.audience(List.of("social-service"))),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.audience(List.of("auth-service", "ltz-api"))),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.subject("svc:auth-service")),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.subject("svc:evil")),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.subject(UUID.randomUUID().toString())),
                craft("at+jwt", signingKey.keyId(), claims -> { }),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.issuer("baska-issuer")),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.expiresAt(Instant.now().plusSeconds(120))),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.issuedAt(Instant.now().minusSeconds(3600))
                        .notBefore(Instant.now().minusSeconds(3600)).expiresAt(Instant.now().minusSeconds(3000))),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.claims(map -> map.remove("iat"))),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.claims(map -> map.remove("jti"))),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.claims(map -> map.remove("nbf"))),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.claims(map -> map.remove("scope"))),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.claim("scope", "account:erase")),
                craft("svc+jwt", signingKey.keyId(), claims -> claims.claim("scope", ALL_SCOPES + " account:erase")));

        for (String token : rejected) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void kapsamDisindaIslemYasak403() throws Exception {
        UUID target = newAccount();
        UUID actor = newAdmin();
        String readOnly = "Bearer " + craft("svc+jwt", signingKey.keyId(), claims -> claims.claim("scope", "account:read"));

        mockMvc.perform(get("/internal/v1/accounts/" + target).header(HttpHeaders.AUTHORIZATION, readOnly))
                .andExpect(status().isOk());
        changeStatus(readOnly, target, actor, "SUSPENDED").andExpect(status().isForbidden());
        role(readOnly, target, actor, "grant", "CONTENT_CREATOR").andExpect(status().isForbidden());
        assertThat(statusOf(target)).isEqualTo("ACTIVE");
    }

    // ---- İşlemler ----

    @Test
    void durumDegisikligiKullaniciUcuylaAyniKurallarlaYapilirDenetimeYoneticiYazilir() throws Exception {
        UUID target = newAccount();
        UUID actor = newAdmin();
        String bearer = serviceBearer();
        String targetEmail = jdbc.queryForObject("select email from accounts where id = ?", String.class, target);
        SessionCookiePair session = loginSession(targetEmail);

        changeStatus(bearer, target, actor, "BANNED").andExpect(status().isNoContent());

        assertThat(statusOf(target)).isEqualTo("BANNED");
        assertThat(jdbc.queryForMap("select actor_id, from_status, to_status, reason from account_status_history "
                + "where account_id = ?", target)).containsEntry("actor_id", actor)
                .containsEntry("from_status", "ACTIVE").containsEntry("to_status", "BANNED")
                .containsEntry("reason", REASON);
        refresh(session.refresh()).andExpect(status().isUnauthorized());
        // Yasak da servis kimliğiyle kaldırılabilir (B8).
        changeStatus(bearer, target, actor, "ACTIVE").andExpect(status().isNoContent());
        assertThat(statusOf(target)).isEqualTo("ACTIVE");
    }

    @Test
    void rolVerilipAlinirHesapOkunur() throws Exception {
        UUID target = newAccount();
        UUID actor = newAdmin();
        String bearer = serviceBearer();

        role(bearer, target, actor, "grant", "CONTENT_CREATOR").andExpect(status().isNoContent());
        mockMvc.perform(get("/internal/v1/accounts/" + target).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[?(@ == 'CONTENT_CREATOR')]").exists());
        role(bearer, target, actor, "revoke", "CONTENT_CREATOR").andExpect(status().isNoContent());
        mockMvc.perform(get("/internal/v1/accounts/" + target).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.roles[?(@ == 'CONTENT_CREATOR')]").doesNotExist());
        // Zorunlu 2FA kuralı servis çağrısı için gevşemez.
        role(bearer, target, actor, "grant", "MODERATOR").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_REQUIRES_TWO_FACTOR"));
    }

    @Test
    void islemiYapanYoneticiAuthtaDogrulanir() throws Exception {
        UUID target = newAccount();
        String bearer = serviceBearer();
        UUID player = newAccount();
        UUID suspendedAdmin = newAdmin();
        jdbc.update("update accounts set status = 'SUSPENDED' where id = ?", suspendedAdmin);
        UUID admin = newAdmin();

        changeStatus(bearer, target, player, "SUSPENDED").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTING_ADMIN_INVALID"));
        changeStatus(bearer, target, suspendedAdmin, "SUSPENDED").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTING_ADMIN_INVALID"));
        changeStatus(bearer, target, UUID.randomUUID(), "SUSPENDED").andExpect(status().isForbidden());
        // Yönetici kendi hesabını değiştiremez (kullanıcı uçlarıyla aynı kural).
        changeStatus(bearer, admin, admin, "SUSPENDED").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION_FORBIDDEN"));
        assertThat(statusOf(target)).isEqualTo("ACTIVE");
    }

    @Test
    void gecersizGovdeVeBilinmeyenAlanReddedilir() throws Exception {
        UUID target = newAccount();
        UUID actor = newAdmin();
        String bearer = serviceBearer();
        String path = "/internal/v1/accounts/" + target + "/status";

        send(bearer, path, "{\"status\":\"SUSPENDED\",\"reason\":\"%s\"}".formatted(REASON)).andExpect(status().isBadRequest());
        send(bearer, path, "{\"actorId\":\"%s\",\"status\":\"SUSPENDED\"}".formatted(actor)).andExpect(status().isBadRequest());
        send(bearer, path, "{\"actorId\":\"%s\",\"status\":\"SUSPENDED\",\"reason\":\"%s\",\"extra\":1}".formatted(actor, REASON))
                .andExpect(status().isBadRequest());
        send(bearer, "/internal/v1/accounts/" + UUID.randomUUID() + "/status",
                "{\"actorId\":\"%s\",\"status\":\"SUSPENDED\",\"reason\":\"%s\"}".formatted(actor, REASON))
                .andExpect(status().isNotFound());
        assertThat(statusOf(target)).isEqualTo("ACTIVE");
    }

    // ---- Yardımcılar ----

    private ResultActions tokenRequest(String clientId, String secret) throws Exception {
        return postJson("/internal/v1/service-tokens", "{\"clientId\":\"%s\",\"clientSecret\":\"%s\"}".formatted(clientId, secret));
    }

    /** Gerçek akış: token ucundan alınan servis token'ı. */
    private String serviceBearer() throws Exception {
        String body = tokenRequest("admin-service", ADMIN_CLIENT_SECRET).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        return "Bearer " + com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
    }

    private ResultActions changeStatus(String bearer, UUID target, UUID actor, String status) throws Exception {
        return send(bearer, "/internal/v1/accounts/" + target + "/status",
                "{\"actorId\":\"%s\",\"status\":\"%s\",\"reason\":\"%s\"}".formatted(actor, status, REASON));
    }

    private ResultActions role(String bearer, UUID target, UUID actor, String action, String role) throws Exception {
        return send(bearer, "/internal/v1/accounts/" + target + "/roles/" + action,
                "{\"actorId\":\"%s\",\"role\":\"%s\",\"reason\":\"%s\"}".formatted(actor, role, REASON));
    }

    private ResultActions send(String bearer, String path, String json) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Geçerli bir token'ın tek alanını bozarak sözleşme ihlalini taklit eder (Auth'un kendi anahtarıyla imzalı). */
    private String craft(String type, String keyId, Consumer<JwtClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer("ltz-auth").subject("svc:admin-service").audience(List.of("auth-service"))
                .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(60))
                .id(UUID.randomUUID().toString()).claim("scope", ALL_SCOPES);
        customize.accept(builder);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type(type).keyId(keyId).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }

    private UUID newAccount() throws Exception {
        return accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
    }

    private UUID newAdmin() throws Exception {
        UUID admin = newAccount();
        enableTotp(admin);
        grantRole(admin, "ADMIN");
        return admin;
    }

    private String statusOf(UUID accountId) {
        return jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId);
    }
}
