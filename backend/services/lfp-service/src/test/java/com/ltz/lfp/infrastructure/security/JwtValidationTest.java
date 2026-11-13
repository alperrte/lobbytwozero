package com.ltz.lfp.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.lfp.IntegrationTestSupport;
import com.ltz.lfp.api.controller.LfpApiPaths;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/** Access token doğrulamasının saldırı senaryoları: her biri korumalı uçta 401 almalıdır (SECURITY.md §8, H-12). */
class JwtValidationTest extends IntegrationTestSupport {

    private static final int ATTACKER_KEY_BITS = 2048;

    @Test
    void gecerliTokenKabulEdilir() throws Exception {
        callProtected(bearer(newUser())).andExpect(status().isCreated());
    }

    @Test
    void tokensizIstek401YanitProblemDetail() throws Exception {
        mockMvc.perform(post(LfpApiPaths.LISTINGS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(LfpApiPaths.LISTINGS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(LfpApiPaths.LISTING, UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void baskaAnahtarlaImzalanmisTokenReddedilir() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(ATTACKER_KEY_BITS).keyID(KEY_ID).generate();
        String forged = sign(attacker, ACCESS_TOKEN_TYPE, validClaims(newUser()).build());

        callProtected("Bearer " + forged).andExpect(status().isUnauthorized());
    }

    @Test
    void yanlisAudienceIssuerVeTurReddedilirServisTokeniKullaniciUcundaGecmez() throws Exception {
        callProtected(bearerWith(claims -> claims.audience("other-api"))).andExpect(status().isUnauthorized());
        callProtected(bearerWith(claims -> claims.issuer("evil-issuer"))).andExpect(status().isUnauthorized());
        callProtected("Bearer " + sign(SIGNING_KEY, JOSEObjectType.JWT, validClaims(newUser()).build()))
                .andExpect(status().isUnauthorized());
        callProtected("Bearer " + sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, validClaims(newUser()).build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subEksikVeyaUuidDegilseReddedilir() throws Exception {
        callProtected(bearerWith(claims -> claims.subject(null))).andExpect(status().isUnauthorized());
        // Geçerli imzalı ama UUID olmayan sub: kimlik çözülemez → 401, 500 değil.
        callProtected(bearerWith(claims -> claims.subject("not-a-uuid"))).andExpect(status().isUnauthorized());
    }

    @Test
    void authinGercekTokenBicimiKabulEdilirSidVerAmrAudDizi() throws Exception {
        String token = accessToken(newUser(), claims -> claims.audience(List.of(AUDIENCE))
                .claim("sid", UUID.randomUUID().toString())
                .claim("ver", 3)
                .claim("amr", List.of("pwd", "otp"))
                .claim("roles", List.of("PLAYER", "CONTENT_CREATOR")));

        callProtected("Bearer " + token).andExpect(status().isCreated());
    }

    @Test
    void bilinmeyenKidReddedilir401() throws Exception {
        RSAKey unknown = new RSAKeyGenerator(ATTACKER_KEY_BITS).keyID("unknown-" + UUID.randomUUID()).generate();

        callProtected("Bearer " + sign(unknown, ACCESS_TOKEN_TYPE, validClaims(newUser()).build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwksAnahtarDonunceBilinmeyenKidIcinJwksYenilenirSonrasindaYeniTokenGecer() throws Exception {
        RSAKey rotated = new RSAKeyGenerator(ATTACKER_KEY_BITS).keyID("rotated-" + UUID.randomUUID()).generate();
        callProtected("Bearer " + sign(rotated, ACCESS_TOKEN_TYPE, validClaims(newUser()).build()))
                .andExpect(status().isUnauthorized());

        publishKey(rotated);

        callProtected("Bearer " + sign(rotated, ACCESS_TOKEN_TYPE, validClaims(newUser()).build()))
                .andExpect(status().isCreated());
        callProtected(bearer(newUser())).andExpect(status().isCreated());
    }

    @Test
    void suresiDolmusVeHenuzGecerliOlmayanTokenReddedilir() throws Exception {
        Instant past = Instant.now().minusSeconds(600);
        callProtected(bearerWith(claims -> claims.issueTime(Date.from(past)).notBeforeTime(Date.from(past))
                .expirationTime(Date.from(past.plusSeconds(60))))).andExpect(status().isUnauthorized());

        Instant future = Instant.now().plusSeconds(3600);
        callProtected(bearerWith(claims -> claims.notBeforeTime(Date.from(future))
                .expirationTime(Date.from(future.plusSeconds(60))))).andExpect(status().isUnauthorized());
    }

    @Test
    void algoritmaKarisikligiReddedilirNoneVeHs256() throws Exception {
        JWTClaimsSet claims = validClaims(newUser()).build();

        callProtected("Bearer " + new PlainJWT(claims).serialize()).andExpect(status().isUnauthorized());

        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .keyID(KEY_ID).type(ACCESS_TOKEN_TYPE).build(), claims);
        hmac.sign(new MACSigner(SIGNING_KEY.toPublicJWK().toRSAPublicKey().getEncoded()));
        callProtected("Bearer " + hmac.serialize()).andExpect(status().isUnauthorized());
    }

    @Test
    void bozulmusImzaVeBozukBasliklarReddedilir() throws Exception {
        String token = accessToken(newUser(), UnaryOperator.identity());
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        callProtected("Bearer " + tampered).andExpect(status().isUnauthorized());
        callProtected("Bearer ").andExpect(status().isUnauthorized());
        callProtected("Bearer abc.def.ghi").andExpect(status().isUnauthorized());
        callProtected("Basic dXNlcjpwYXNz").andExpect(status().isUnauthorized());
    }

    @Test
    void tanimsizYollarKorunurDenyByDefaultIcYollarKapali() throws Exception {
        mockMvc.perform(get("/api/v1/lfp/unknown")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/other/anything").header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/internal/v1/anything").header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void saglikUcuHerkeseAcik() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    /** Korumalı uç: geçerli gövdeyle ilan oluşturma (her çağrıda yeni oyun; sınırlara takılmaz). */
    private ResultActions callProtected(String authorization) throws Exception {
        return mockMvc.perform(post(LfpApiPaths.LISTINGS)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(listingBody(games.addGame("Protected")))));
    }

    private static String bearerWith(UnaryOperator<JWTClaimsSet.Builder> change) {
        return "Bearer " + accessToken(newUser(), change);
    }
}
