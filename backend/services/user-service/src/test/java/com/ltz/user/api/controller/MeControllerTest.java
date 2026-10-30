package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.ltz.user.IntegrationTestSupport;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/** Oyuncunun kendi profili (USER-01/02): {@code GET /me}, {@code PATCH /me/profile} ve kullanıcı token'ı kuralları. */
class MeControllerTest extends IntegrationTestSupport {

    // ------------------------------------------------------------ USER-01: profil

    @Test
    void ilkIstekteVarsayilanProfilOlusur() throws Exception {
        UUID user = newUser();

        me(user).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.userId").value(user.toString()))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.displayName").doesNotExist())
                .andExpect(jsonPath("$.playerTypes").isEmpty())
                .andExpect(jsonPath("$.favoriteGenres").isEmpty())
                .andExpect(jsonPath("$.version").value(0));

        assertThat(profileCount(user)).isEqualTo(1);
    }

    @Test
    void ikinciIstekAyniProfiliDonerTekSatir() throws Exception {
        UUID user = newUser();

        me(user).andExpect(status().isOk());
        me(user).andExpect(status().isOk());

        assertThat(profileCount(user)).isEqualTo(1);
    }

    @Test
    void eszamanliIlkIsteklerTekProfilOlusurHepsi200() throws Exception {
        UUID user = newUser();
        Callable<Integer> request = () -> me(user).andReturn().getResponse().getStatus();

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            for (var result : pool.invokeAll(List.of(request, request, request, request))) {
                assertThat(result.get()).isEqualTo(200);
            }
        }
        assertThat(profileCount(user)).isEqualTo(1);
    }

    @Test
    void herKullaniciYalnizKendiProfiliniGorur() throws Exception {
        UUID first = newUser();
        UUID second = newUser();
        patch(first, "{\"version\":0,\"displayName\":\"Birinci\"}").andExpect(status().isOk());

        me(second).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").doesNotExist());
        me(first).andExpect(jsonPath("$.displayName").value("Birinci"));
    }

    // ------------------------------------------------------------ USER-02: düzenleme

    @Test
    void profilGuncellenirSurumArtar() throws Exception {
        UUID user = newUser();

        patch(user, """
                {"version":0,"displayName":"Gölge Avcı","bio":"Merhaba\\ndünya","country":"tr",
                 "playerTypes":["competitive","social"],"favoriteGenres":["rpg","Strategy"],
                 "favoritePlatforms":["pc"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Gölge Avcı"))
                .andExpect(jsonPath("$.bio").value("Merhaba\ndünya"))
                .andExpect(jsonPath("$.country").value("TR"))
                .andExpect(jsonPath("$.playerTypes[0]").value("COMPETITIVE"))
                .andExpect(jsonPath("$.playerTypes[1]").value("SOCIAL"))
                .andExpect(jsonPath("$.favoriteGenres[1]").value("strategy"))
                .andExpect(jsonPath("$.favoritePlatforms[0]").value("pc"))
                .andExpect(jsonPath("$.version").value(1));

        me(user).andExpect(jsonPath("$.displayName").value("Gölge Avcı"));
    }

    @Test
    void gonderilmeyenAlanlarDegismezBosDegerTemizler() throws Exception {
        UUID user = newUser();
        patch(user, "{\"version\":0,\"displayName\":\"Oyuncu\",\"bio\":\"Biyografi\",\"favoriteGenres\":[\"rpg\"]}")
                .andExpect(status().isOk());

        patch(user, "{\"version\":1,\"displayName\":\"Yeni Ad\"}")
                .andExpect(jsonPath("$.bio").value("Biyografi"))
                .andExpect(jsonPath("$.favoriteGenres[0]").value("rpg"));
        patch(user, "{\"version\":2,\"bio\":\"\",\"favoriteGenres\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.favoriteGenres").isEmpty())
                .andExpect(jsonPath("$.displayName").value("Yeni Ad"));
    }

    @Test
    void degisiklikYoksaSurumArtmaz() throws Exception {
        UUID user = newUser();
        patch(user, "{\"version\":0,\"displayName\":\"Sabit\"}").andExpect(jsonPath("$.version").value(1));

        patch(user, "{\"version\":1,\"displayName\":\"Sabit\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void eskiSurumle409VeriDegismez() throws Exception {
        UUID user = newUser();
        patch(user, "{\"version\":0,\"displayName\":\"Ilk\"}").andExpect(status().isOk());

        patch(user, "{\"version\":0,\"displayName\":\"Eski\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        me(user).andExpect(jsonPath("$.displayName").value("Ilk"));
    }

    @Test
    void eszamanliGuncellemelerYalnizBiriBasarili() throws Exception {
        UUID user = newUser();
        me(user).andExpect(status().isOk());
        Callable<Integer> first = () -> patch(user, "{\"version\":0,\"displayName\":\"Birinci\"}")
                .andReturn().getResponse().getStatus();
        Callable<Integer> second = () -> patch(user, "{\"version\":0,\"displayName\":\"Ikinci\"}")
                .andReturn().getResponse().getStatus();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(first, second));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    void surumZorunluEksikseVeyaNegatifse400() throws Exception {
        UUID user = newUser();

        patch(user, "{\"displayName\":\"Surumsuz\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        patch(user, "{\"version\":-1,\"displayName\":\"Negatif\"}").andExpect(status().isBadRequest());
    }

    @Test
    void gecersizAlanlar400() throws Exception {
        UUID user = newUser();

        patch(user, "{\"version\":0,\"displayName\":\"A\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DISPLAY_NAME"));
        patch(user, "{\"version\":0,\"displayName\":\"%s\"}".formatted("a".repeat(33)))
                .andExpect(jsonPath("$.code").value("INVALID_DISPLAY_NAME"));
        patch(user, "{\"version\":0,\"displayName\":\"Ad\\u202Esoyad\"}")
                .andExpect(jsonPath("$.code").value("INVALID_DISPLAY_NAME"));
        patch(user, "{\"version\":0,\"displayName\":\"Iki\\nSatir\"}")
                .andExpect(jsonPath("$.code").value("INVALID_DISPLAY_NAME"));
        patch(user, "{\"version\":0,\"bio\":\"%s\"}".formatted("b".repeat(501)))
                .andExpect(jsonPath("$.code").value("INVALID_BIO"));
        patch(user, "{\"version\":0,\"country\":\"ZZ\"}").andExpect(jsonPath("$.code").value("INVALID_COUNTRY"));
        patch(user, "{\"version\":0,\"playerTypes\":[\"CASUAL\",\"STORY\",\"SOCIAL\"]}")
                .andExpect(jsonPath("$.code").value("INVALID_PLAYER_TYPES"));
        patch(user, "{\"version\":0,\"playerTypes\":[\"WIZARD\"]}")
                .andExpect(jsonPath("$.code").value("INVALID_PLAYER_TYPES"));
        patch(user, "{\"version\":0,\"favoriteGenres\":[\"rpg!\"]}")
                .andExpect(jsonPath("$.code").value("INVALID_FAVORITE_GENRES"));
        patch(user, "{\"version\":0,\"favoritePlatforms\":[%s]}".formatted(
                String.join(",", java.util.stream.IntStream.range(0, 11).mapToObj(i -> "\"p" + i + "\"").toList())))
                .andExpect(jsonPath("$.code").value("INVALID_FAVORITE_PLATFORMS"));

        me(user).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void govdedeKimlikAlaniYoksayilmazReddedilirBaskasininProfiliDegismez() throws Exception {
        UUID attacker = newUser();
        UUID victim = newUser();
        patch(victim, "{\"version\":0,\"displayName\":\"Kurban\"}").andExpect(status().isOk());

        patch(attacker, "{\"version\":0,\"userId\":\"%s\",\"displayName\":\"Saldirgan\"}".formatted(victim))
                .andExpect(status().isBadRequest());

        me(victim).andExpect(jsonPath("$.displayName").value("Kurban"));
    }

    @Test
    void tanimsizAlan400() throws Exception {
        patch(newUser(), "{\"version\":0,\"accountStatus\":\"BANNED\"}").andExpect(status().isBadRequest());
    }

    @Test
    void bozukJson400() throws Exception {
        patch(newUser(), "{\"version\":").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void asiriBuyukGovde413() throws Exception {
        patch(newUser(), "{\"version\":0,\"bio\":\"%s\"}".formatted("x".repeat(20_000)))
                .andExpect(status().isContentTooLarge());
    }

    // ------------------------------------------------------------ kullanıcı token'ı kuralları

    @Test
    void tokensiz401() throws Exception {
        mockMvc.perform(get(UserApiPaths.ME)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(MockMvcRequestBuilders.patch(UserApiPaths.ME_PROFILE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}")).andExpect(status().isUnauthorized());
    }

    @Test
    void yanlisAudienceIssuerVeTur401() throws Exception {
        UUID user = newUser();

        mePath(accessToken(user, claims -> claims.audience("social-service"))).andExpect(status().isUnauthorized());
        mePath(accessToken(user, claims -> claims.issuer("evil-issuer"))).andExpect(status().isUnauthorized());
        mePath(sign(SIGNING_KEY, JOSEObjectType.JWT, validClaims(user).build())).andExpect(status().isUnauthorized());
        mePath(sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, validClaims(user).build())).andExpect(status().isUnauthorized());
    }

    @Test
    void subUuidDegilse401() throws Exception {
        mePath(accessToken(UUID.randomUUID(), claims -> claims.subject("not-a-uuid"))).andExpect(status().isUnauthorized());
        mePath(accessToken(UUID.randomUUID(), claims -> claims.subject("svc:auth-service")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sureKurallari401() throws Exception {
        UUID user = newUser();
        Instant now = Instant.now();

        mePath(accessToken(user, claims -> claims.issueTime(Date.from(now.minusSeconds(900)))
                .notBeforeTime(Date.from(now.minusSeconds(900))).expirationTime(Date.from(now.minusSeconds(600)))))
                .andExpect(status().isUnauthorized());
        mePath(accessToken(user, claims -> claims.notBeforeTime(Date.from(now.plusSeconds(600)))))
                .andExpect(status().isUnauthorized());
        mePath(accessToken(user, claims -> claims.expirationTime(null))).andExpect(status().isUnauthorized());
        mePath(accessToken(user, claims -> claims.issueTime(null))).andExpect(status().isUnauthorized());
    }

    @Test
    void imzaSaldirilari401() throws Exception {
        UUID user = newUser();
        RSAKey attacker = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        JWTClaimsSet claims = validClaims(user).build();

        mePath(sign(attacker, ACCESS_TOKEN_TYPE, claims)).andExpect(status().isUnauthorized());
        mePath(new PlainJWT(claims).serialize()).andExpect(status().isUnauthorized());
        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY_ID)
                .type(ACCESS_TOKEN_TYPE).build(), claims);
        hmac.sign(new MACSigner(SIGNING_KEY.toPublicJWK().toRSAPublicKey().getEncoded()));
        mePath(hmac.serialize()).andExpect(status().isUnauthorized());
        String token = accessToken(user, UnaryOperator.identity());
        mePath(token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tanimsizYollarKapali() throws Exception {
        mockMvc.perform(get("/api/v1/user/me/unknown").header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/somewhere-else").header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ yardımcılar

    private ResultActions me(UUID user) throws Exception {
        return mockMvc.perform(get(UserApiPaths.ME).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions mePath(String token) throws Exception {
        return mockMvc.perform(get(UserApiPaths.ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions patch(UUID user, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.patch(UserApiPaths.ME_PROFILE)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
