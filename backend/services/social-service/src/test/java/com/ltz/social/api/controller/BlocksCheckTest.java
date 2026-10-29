package com.ltz.social.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.social.IntegrationTestSupport;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * messaging/lfp → Social engel sorgusu (SOC-15). Yalnız {@code scope=blocks:check} taşıyan ve çağıranı messaging ya da
 * lfp olan servis token'ı kabul edilir.
 */
class BlocksCheckTest extends IntegrationTestSupport {

    // ------------------------------------------------------------ sorgu davranışı

    @Test
    void engelHerIkiYondeDeBulunurEngelsizHesapFalse() throws Exception {
        UUID user = newUser();
        UUID blockedByUser = newUser();
        UUID blockerOfUser = newUser();
        UUID unrelated = newUser();
        block(user, blockedByUser).andExpect(status().isCreated());
        block(blockerOfUser, user).andExpect(status().isCreated());

        check("messaging-service", user, blockedByUser, blockerOfUser, unrelated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].userId").value(blockedByUser.toString()))
                .andExpect(jsonPath("$.results[0].blocked").value(true))
                .andExpect(jsonPath("$.results[1].userId").value(blockerOfUser.toString()))
                .andExpect(jsonPath("$.results[1].blocked").value(true))
                .andExpect(jsonPath("$.results[2].userId").value(unrelated.toString()))
                .andExpect(jsonPath("$.results[2].blocked").value(false));
    }

    @Test
    void engelKaldirilincaSorguFalseDoner() throws Exception {
        UUID user = newUser();
        UUID other = newUser();
        block(user, other).andExpect(status().isCreated());
        unblock(user, other).andExpect(status().isNoContent());

        check("lfp-service", user, other).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].blocked").value(false));
    }

    @Test
    void baskalarininEngeliSorguyuEtkilemez() throws Exception {
        UUID user = newUser();
        UUID other = newUser();
        block(newUser(), other).andExpect(status().isCreated());
        block(other, newUser()).andExpect(status().isCreated());

        check("lfp-service", user, other).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].blocked").value(false));
    }

    @Test
    void yinelenenKimlikTekSonucDonerKendisiEngelsiz() throws Exception {
        UUID user = newUser();
        UUID other = newUser();
        block(user, other).andExpect(status().isCreated());

        check("messaging-service", user, other, other, user).andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].blocked").value(true))
                .andExpect(jsonPath("$.results[1].userId").value(user.toString()))
                .andExpect(jsonPath("$.results[1].blocked").value(false));
    }

    @Test
    void yuzKimlikKabulEdilirYuzbirinciReddedilir() throws Exception {
        UUID user = newUser();
        String token = blocksCheckToken("lfp-service", UnaryOperator.identity());

        mockMvc.perform(request(token, user, manyUsers(100)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(100));
        mockMvc.perform(request(token, user, manyUsers(101)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void gecersizGovde400BilinmeyenAlanDa() throws Exception {
        String token = blocksCheckToken("messaging-service", UnaryOperator.identity());
        UUID user = newUser();

        // Boş liste, eksik userId, UUID olmayan değer ve tanımsız alan.
        for (String body : List.of(
                "{\"userId\":\"%s\",\"otherUserIds\":[]}".formatted(user),
                "{\"otherUserIds\":[\"%s\"]}".formatted(newUser()),
                "{\"userId\":\"%s\",\"otherUserIds\":[\"not-a-uuid\"]}".formatted(user),
                "{\"userId\":\"%s\",\"otherUserIds\":[\"%s\"],\"extra\":1}".formatted(user, newUser()))) {
            mockMvc.perform(post(InternalApiPaths.BLOCKS_CHECK)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------ kimlik ve yetki

    @Test
    void tokensizCagri401() throws Exception {
        mockMvc.perform(post(InternalApiPaths.BLOCKS_CHECK).contentType(MediaType.APPLICATION_JSON)
                        .content(body(newUser(), newUser())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void kullaniciTokeniIcUcuAcamaz401() throws Exception {
        UUID user = newUser();

        mockMvc.perform(post(InternalApiPaths.BLOCKS_CHECK)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body(user, newUser())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void silmeKapsamliTokenEngelSorgulayamaz403() throws Exception {
        UUID user = newUser();
        // Auth'un silme token'ı (account:erase) engel sorgusuna yetmez.
        String eraseToken = serviceToken(user, UnaryOperator.identity());

        mockMvc.perform(request(eraseToken, user, List.of(newUser())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void kapsamEksikVeyaFarkliysa403() throws Exception {
        UUID user = newUser();

        List<UnaryOperator<JWTClaimsSet.Builder>> changes = List.of(
                claims -> claims.claim("scope", null),
                claims -> claims.claim("scope", "blocks:read"),
                claims -> claims.claim("scope", "blocks:check-all"));
        for (UnaryOperator<JWTClaimsSet.Builder> change : changes) {
            mockMvc.perform(request(blocksCheckToken("messaging-service", change), user, List.of(newUser())))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void kapsamDogruAmaCagiranMessagingVeLfpDegilse403() throws Exception {
        UUID user = newUser();

        for (String caller : List.of("admin-service", "community-service", "auth-service", "messaging-service-evil")) {
            mockMvc.perform(request(blocksCheckToken(caller, UnaryOperator.identity()), user, List.of(newUser())))
                    .andExpect(status().isForbidden());
        }
        for (String caller : List.of("messaging-service", "lfp-service")) {
            mockMvc.perform(request(blocksCheckToken(caller, UnaryOperator.identity()), user, List.of(newUser())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void yanlisAudienceIssuerVeTur401() throws Exception {
        UUID user = newUser();
        List<UUID> others = List.of(newUser());

        mockMvc.perform(request(blocksCheckToken("lfp-service", claims -> claims.audience("community-service")), user,
                others)).andExpect(status().isUnauthorized());
        mockMvc.perform(request(blocksCheckToken("lfp-service", claims -> claims.issuer("evil-issuer")), user, others))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(sign(SIGNING_KEY, JOSEObjectType.JWT,
                serviceClaims("svc:lfp-service", BLOCKS_CHECK_SCOPE).build()), user, others))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(sign(SIGNING_KEY, ACCESS_TOKEN_TYPE,
                serviceClaims("svc:lfp-service", BLOCKS_CHECK_SCOPE).build()), user, others))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void servisTokeniBaskaYontemlerdeVeKullaniciUclarindaGecmez() throws Exception {
        String bearer = "Bearer " + blocksCheckToken("messaging-service", UnaryOperator.identity());

        mockMvc.perform(get(InternalApiPaths.BLOCKS_CHECK).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(SocialApiPaths.FRIENDS).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ yardımcılar

    private ResultActions check(String caller, UUID user, UUID... others) throws Exception {
        return mockMvc.perform(request(blocksCheckToken(caller, UnaryOperator.identity()), user, List.of(others)));
    }

    private static RequestBuilder request(String token, UUID user, List<UUID> others) {
        return post(InternalApiPaths.BLOCKS_CHECK)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(user, others.toArray(UUID[]::new)));
    }

    private static String body(UUID user, UUID... others) {
        String ids = Stream.of(others).map(id -> "\"" + id + "\"").reduce((a, b) -> a + "," + b).orElse("");
        return "{\"userId\":\"%s\",\"otherUserIds\":[%s]}".formatted(user, ids);
    }

    private static List<UUID> manyUsers(int count) {
        return Stream.generate(UUID::randomUUID).limit(count).toList();
    }
}
