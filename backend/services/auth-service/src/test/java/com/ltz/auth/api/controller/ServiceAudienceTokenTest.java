package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.infrastructure.security.JwtSigningKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Servisler arası olay girişi: servis istemcileri yalnız kendilerine tanımlı (hedef, kapsam) çiftleri için token alır.
 * Kimseye varsayılan imtiyaz yoktur; hedef servise bağlı token Auth'un iç uçlarında geçmez.
 */
class ServiceAudienceTokenTest extends IntegrationTestSupport {

    @Autowired
    private JwtSigningKey signingKey;

    @Test
    void socialCommunityIcinOlayGirisiTokeniAlirKapsamHedefeBaglidir() throws Exception {
        String body = tokenRequest("social-service", SOCIAL_CLIENT_SECRET, "community-service")
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("events:ingest"))
                .andReturn().getResponse().getContentAsString();

        SignedJWT jwt = SignedJWT.parse(JsonPath.read(body, "$.accessToken"));
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(jwt.getHeader().getType().getType()).isEqualTo("svc+jwt");
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(signingKey.keyId());
        assertThat(claims.getAudience()).containsExactly("community-service");
        assertThat(claims.getSubject()).isEqualTo("svc:social-service");
        assertThat(claims.getStringClaim("scope")).isEqualTo("events:ingest");
        assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()).isEqualTo(60_000L);
        assertThat(claims.getClaims()).doesNotContainKeys("roles", "sid", "ver", "amr", "account_id");
    }

    @Test
    void communityAdminVeNotificationIcinAdminCommunityIcinTokenAlir() throws Exception {
        assertThat(audienceOf(tokenRequest("community-service", COMMUNITY_CLIENT_SECRET, "admin-service")))
                .containsExactly("admin-service");
        assertThat(audienceOf(tokenRequest("community-service", COMMUNITY_CLIENT_SECRET, "notification-service")))
                .containsExactly("notification-service");
        assertThat(audienceOf(tokenRequest("admin-service", ADMIN_CLIENT_SECRET, "community-service")))
                .containsExactly("community-service");
    }

    @Test
    void messagingVeLfpYalnizSocialIcinEngelSorgusuTokeniAlir() throws Exception {
        String body = tokenRequest("messaging-service", MESSAGING_CLIENT_SECRET, "social-service")
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("blocks:check"))
                .andReturn().getResponse().getContentAsString();
        JWTClaimsSet claims = SignedJWT.parse(JsonPath.<String>read(body, "$.accessToken")).getJWTClaimsSet();
        assertThat(claims.getAudience()).containsExactly("social-service");
        assertThat(claims.getSubject()).isEqualTo("svc:messaging-service");
        assertThat(claims.getStringClaim("scope")).isEqualTo("blocks:check");
        assertThat(audienceOf(tokenRequest("lfp-service", LFP_CLIENT_SECRET, "social-service")))
                .containsExactly("social-service");

        // Kapsam yalnız o hedefe bağlıdır: başka hedef, Auth ve olay girişi verilmez.
        tokenRequest("messaging-service", MESSAGING_CLIENT_SECRET, "community-service").andExpect(status().isForbidden());
        tokenRequest("lfp-service", LFP_CLIENT_SECRET, "notification-service").andExpect(status().isForbidden());
        tokenRequest("lfp-service", LFP_CLIENT_SECRET, "auth-service").andExpect(status().isForbidden());
        tokenRequest("messaging-service", MESSAGING_CLIENT_SECRET, null).andExpect(status().isForbidden());
        // Social kendisine engel sorgusu token'ı alamaz; başkasının anahtarı da geçmez.
        tokenRequest("social-service", SOCIAL_CLIENT_SECRET, "social-service").andExpect(status().isForbidden());
        tokenRequest("lfp-service", MESSAGING_CLIENT_SECRET, "social-service").andExpect(status().isUnauthorized());
    }

    @Test
    void userServiceIcinGizlilikKarariVeOzetOlayiTokenleriIzinListesinedir() throws Exception {
        // REQ-04: hangi istemci user-service'e hangi kapsamla gidebilir. Kullanıcı bağı (account_id) hiçbirinde yok.
        for (Object[] pair : new Object[][] {
                {"social-service", SOCIAL_CLIENT_SECRET, "events:ingest privacy:read"},
                {"library-service", LIBRARY_CLIENT_SECRET, "events:ingest privacy:read"},
                {"hardware-service", HARDWARE_CLIENT_SECRET, "privacy:read"},
                {"review-service", REVIEW_CLIENT_SECRET, "events:ingest"}}) {
            String body = tokenRequest((String) pair[0], (String) pair[1], "user-service")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value(pair[2]))
                    .andReturn().getResponse().getContentAsString();
            JWTClaimsSet claims = SignedJWT.parse(JsonPath.<String>read(body, "$.accessToken")).getJWTClaimsSet();
            assertThat(claims.getAudience()).containsExactly("user-service");
            assertThat(claims.getSubject()).isEqualTo("svc:" + pair[0]);
            assertThat(claims.getStringClaim("scope")).isEqualTo(pair[2]);
            assertThat(claims.getClaims()).doesNotContainKeys("roles", "sid", "ver", "amr", "account_id");
        }

        // Kapsam hedefe ve çifte bağlıdır: review gizlilik kararı alamaz, hardware olay yazamaz.
        tokenRequest("library-service", LIBRARY_CLIENT_SECRET, "social-service").andExpect(status().isForbidden());
        tokenRequest("library-service", LIBRARY_CLIENT_SECRET, null).andExpect(status().isForbidden());
        tokenRequest("review-service", REVIEW_CLIENT_SECRET, "social-service").andExpect(status().isForbidden());
        tokenRequest("hardware-service", HARDWARE_CLIENT_SECRET, "community-service").andExpect(status().isForbidden());
        // User kimseyi çağırmaz: istemcisi yoktur, kimlik bilgisi tanınmaz.
        tokenRequest("user-service", LIBRARY_CLIENT_SECRET, "social-service").andExpect(status().isUnauthorized());
        // Başkalarının yetkisi genişlemedi: messaging yalnız blocks:check alır.
        tokenRequest("messaging-service", MESSAGING_CLIENT_SECRET, "social-service")
                .andExpect(jsonPath("$.scope").value("blocks:check"));
        tokenRequest("messaging-service", MESSAGING_CLIENT_SECRET, "user-service").andExpect(status().isForbidden());
    }

    @Test
    void gameYalnizReviewIcinPuanOzetiOkumaTokeniAlirKullaniciBagiTasimaz() throws Exception {
        String body = tokenRequest("game-service", GAME_CLIENT_SECRET, "review-service")
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("rating-summaries:read"))
                .andReturn().getResponse().getContentAsString();
        JWTClaimsSet claims = SignedJWT.parse(JsonPath.<String>read(body, "$.accessToken")).getJWTClaimsSet();
        assertThat(claims.getAudience()).containsExactly("review-service");
        assertThat(claims.getSubject()).isEqualTo("svc:game-service");
        assertThat(claims.getClaims()).doesNotContainKeys("roles", "sid", "ver", "amr", "account_id");

        // Yalnız okuma: Game ne olay yazabilir ne de başka hedefe token alabilir.
        tokenRequest("game-service", GAME_CLIENT_SECRET, "admin-service").andExpect(status().isForbidden());
        tokenRequest("game-service", GAME_CLIENT_SECRET, "user-service").andExpect(status().isForbidden());
        tokenRequest("game-service", GAME_CLIENT_SECRET, null).andExpect(status().isForbidden());
        // Review'a yalnız bu çift açık: user-service'e giden review istemcisi özet okuma kapsamı alamaz.
        tokenRequest("review-service", REVIEW_CLIENT_SECRET, "review-service").andExpect(status().isForbidden());
    }

    @Test
    void hardwarePerformanceIcinCihazDogrulamaTokeniniYalnizAktifTekHesabaBagliAlir() throws Exception {
        UUID account = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        String body = postJson("/internal/v1/service-tokens",
                "{\"clientId\":\"hardware-service\",\"clientSecret\":\"%s\",\"audience\":\"performance-service\",\"accountId\":\"%s\"}"
                        .formatted(HARDWARE_CLIENT_SECRET, account))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("devices:verify"))
                .andReturn().getResponse().getContentAsString();
        JWTClaimsSet claims = SignedJWT.parse(JsonPath.<String>read(body, "$.accessToken")).getJWTClaimsSet();
        assertThat(claims.getAudience()).containsExactly("performance-service");
        assertThat(claims.getSubject()).isEqualTo("svc:hardware-service");
        assertThat(claims.getStringClaim("account_id")).isEqualTo(account.toString());
        assertThat(claims.getClaims()).doesNotContainKeys("roles", "sid", "ver", "amr");

        // Hesapsız istek, olmayan hesap ve askıdaki hesap reddedilir; user-service'e giden token hesaba bağlı değildir.
        tokenRequest("hardware-service", HARDWARE_CLIENT_SECRET, "performance-service")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SERVICE_ACCOUNT_BINDING_INVALID"));
        postJson("/internal/v1/service-tokens",
                "{\"clientId\":\"hardware-service\",\"clientSecret\":\"%s\",\"audience\":\"performance-service\",\"accountId\":\"%s\"}"
                        .formatted(HARDWARE_CLIENT_SECRET, UUID.randomUUID()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SERVICE_TARGET_ACCOUNT_UNAVAILABLE"));
        tokenRequest("hardware-service", HARDWARE_CLIENT_SECRET, "user-service").andExpect(status().isOk());
        // Başka istemciler Performance'a cihaz doğrulama için token alamaz.
        tokenRequest("library-service", LIBRARY_CLIENT_SECRET, "performance-service").andExpect(status().isForbidden());
        tokenRequest("game-service", GAME_CLIENT_SECRET, "performance-service").andExpect(status().isForbidden());
    }

    @Test
    void recommendationYalnizAktifTekHesabaBagliSinyalTokeniAlir() throws Exception {
        UUID account = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        String body = boundTokenRequest(account.toString())
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("signals:read"))
                .andReturn().getResponse().getContentAsString();
        JWTClaimsSet claims = SignedJWT.parse(JsonPath.<String>read(body, "$.accessToken")).getJWTClaimsSet();
        assertThat(claims.getAudience()).containsExactly("library-service");
        assertThat(claims.getSubject()).isEqualTo("svc:recommendation-service");
        assertThat(claims.getStringClaim("account_id")).isEqualTo(account.toString());
        assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()).isEqualTo(60_000L);
        assertThat(claims.getClaims()).doesNotContainKeys("roles", "sid", "ver", "amr");

        // Hesapsız istek, olmayan hesap, bozuk kimlik ve hesaba bağlı olmayan hedefe hesap eklemek reddedilir.
        tokenRequest("recommendation-service", RECOMMENDATION_CLIENT_SECRET, "library-service")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SERVICE_ACCOUNT_BINDING_INVALID"));
        boundTokenRequest(UUID.randomUUID().toString())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SERVICE_TARGET_ACCOUNT_UNAVAILABLE"));
        boundTokenRequest("hesap-degil").andExpect(status().isBadRequest());
        postJson("/internal/v1/service-tokens", "{\"clientId\":\"social-service\",\"clientSecret\":\"%s\",\"audience\":\"community-service\",\"accountId\":\"%s\"}"
                .formatted(SOCIAL_CLIENT_SECRET, account))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SERVICE_ACCOUNT_BINDING_INVALID"));
        tokenRequest("recommendation-service", RECOMMENDATION_CLIENT_SECRET, "user-service").andExpect(status().isForbidden());
    }

    @Test
    void askidakiHesapIcinSinyalTokeniVerilmez() throws Exception {
        UUID account = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        jdbc.update("update accounts set status = 'SUSPENDED' where id = ?", account);

        boundTokenRequest(account.toString())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SERVICE_TARGET_ACCOUNT_UNAVAILABLE"));
    }

    @Test
    void izinListesindeOlmayanHedefReddedilirAuthKapsamiSizmaz() throws Exception {
        // social yalnız community'ye yazabilir.
        tokenRequest("social-service", SOCIAL_CLIENT_SECRET, "notification-service")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SERVICE_AUDIENCE_FORBIDDEN"));
        tokenRequest("social-service", SOCIAL_CLIENT_SECRET, "game-service").andExpect(status().isForbidden());
        // Auth'un iç kapsamları yalnız admin-service'te; hedefli istemciler Auth için token alamaz (audience verilmese de).
        tokenRequest("social-service", SOCIAL_CLIENT_SECRET, "auth-service").andExpect(status().isForbidden());
        tokenRequest("social-service", SOCIAL_CLIENT_SECRET, null).andExpect(status().isForbidden());
        tokenRequest("community-service", COMMUNITY_CLIENT_SECRET, "auth-service").andExpect(status().isForbidden());
        // Başka istemcinin gizli anahtarı kimlik doğrulamasında düşer, hedef bilgisi sızmaz.
        tokenRequest("social-service", COMMUNITY_CLIENT_SECRET, "community-service").andExpect(status().isUnauthorized());
        tokenRequest("social-service", "yanlis-anahtar-yanlis-anahtar-yanlis-anahtar", "notification-service")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CLIENT"));
    }

    @Test
    void audienceVerilmezseAdminAuthIcinTokenAlmayaDevamEder() throws Exception {
        assertThat(audienceOf(tokenRequest("admin-service", ADMIN_CLIENT_SECRET, null))).containsExactly("auth-service");
        tokenRequest("admin-service", ADMIN_CLIENT_SECRET, "auth-service").andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("account:read account:role:write account:status:write"));
    }

    @Test
    void gecersizAudienceBicimiReddedilir() throws Exception {
        tokenRequest("social-service", SOCIAL_CLIENT_SECRET, "Community;DROP").andExpect(status().isBadRequest());
        tokenRequest("social-service", SOCIAL_CLIENT_SECRET, "a".repeat(33)).andExpect(status().isBadRequest());
    }

    @Test
    void hedefServiseBagliTokenAuthIcUclarindaGecmez() throws Exception {
        String body = tokenRequest("admin-service", ADMIN_CLIENT_SECRET, "community-service")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(body, "$.accessToken");
        UUID account = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        mockMvc.perform(get("/internal/v1/accounts/" + account).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
    }

    private List<String> audienceOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return SignedJWT.parse(JsonPath.<String>read(body, "$.accessToken")).getJWTClaimsSet().getAudience();
    }

    private ResultActions boundTokenRequest(String accountId) throws Exception {
        return postJson("/internal/v1/service-tokens",
                "{\"clientId\":\"recommendation-service\",\"clientSecret\":\"%s\",\"audience\":\"library-service\",\"accountId\":\"%s\"}"
                        .formatted(RECOMMENDATION_CLIENT_SECRET, accountId));
    }

    private ResultActions tokenRequest(String clientId, String secret, String audience) throws Exception {
        String audienceField = audience == null ? "" : ",\"audience\":\"%s\"".formatted(audience);
        return postJson("/internal/v1/service-tokens",
                "{\"clientId\":\"%s\",\"clientSecret\":\"%s\"%s}".formatted(clientId, secret, audienceField));
    }
}
