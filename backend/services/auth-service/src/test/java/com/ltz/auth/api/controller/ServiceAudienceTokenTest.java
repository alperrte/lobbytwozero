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

    private ResultActions tokenRequest(String clientId, String secret, String audience) throws Exception {
        String audienceField = audience == null ? "" : ",\"audience\":\"%s\"".formatted(audience);
        return postJson("/internal/v1/service-tokens",
                "{\"clientId\":\"%s\",\"clientSecret\":\"%s\"%s}".formatted(clientId, secret, audienceField));
    }
}
