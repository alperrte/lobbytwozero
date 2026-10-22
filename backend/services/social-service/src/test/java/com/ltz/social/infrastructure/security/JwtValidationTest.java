package com.ltz.social.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.api.controller.SocialApiPaths;
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
        callProtected(bearer(newUser())).andExpect(status().isOk());
    }

    @Test
    void tokensizIstek401_yanitProblemDetail() throws Exception {
        mockMvc.perform(get(SocialApiPaths.FRIEND_REQUESTS_INCOMING))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUEST_ACCEPT, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUEST_REJECT, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUEST_CANCEL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SocialApiPaths.FRIEND_REQUESTS_OUTGOING))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SocialApiPaths.FRIENDS)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(SocialApiPaths.FRIEND, UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SocialApiPaths.USER_FRIENDS, UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SocialApiPaths.BLOCKS)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(SocialApiPaths.BLOCK, UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void baskaAnahtarlaImzalanmisTokenReddedilir() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(ATTACKER_KEY_BITS).keyID(KEY_ID).generate();
        String forged = sign(attacker, ACCESS_TOKEN_TYPE, validClaims(newUser()).build());

        callProtected("Bearer " + forged).andExpect(status().isUnauthorized());
    }

    @Test
    void yanlisAudienceIssuerVeTurReddedilir() throws Exception {
        callProtected(bearerWith(claims -> claims.audience("other-api"))).andExpect(status().isUnauthorized());
        callProtected(bearerWith(claims -> claims.issuer("evil-issuer"))).andExpect(status().isUnauthorized());
        callProtected("Bearer " + sign(SIGNING_KEY, JOSEObjectType.JWT, validClaims(newUser()).build()))
                .andExpect(status().isUnauthorized());
        callProtected("Bearer " + sign(SIGNING_KEY, new JOSEObjectType("svc+jwt"), validClaims(newUser()).build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subEksikVeyaUuidDegilseReddedilir() throws Exception {
        callProtected(bearerWith(claims -> claims.subject(null))).andExpect(status().isUnauthorized());
        // Geçerli imzalı ama UUID olmayan sub: kimlik çözülemez → 401, 500 değil.
        callProtected(bearerWith(claims -> claims.subject("not-a-uuid"))).andExpect(status().isUnauthorized());
    }

    @Test
    void suresiDolmusTokenReddedilir() throws Exception {
        Instant past = Instant.now().minusSeconds(600);

        callProtected(bearerWith(claims -> claims.issueTime(Date.from(past)).notBeforeTime(Date.from(past))
                .expirationTime(Date.from(past.plusSeconds(60))))).andExpect(status().isUnauthorized());
    }

    @Test
    void henuzGecerliOlmayanTokenReddedilir() throws Exception {
        Instant future = Instant.now().plusSeconds(3600);

        callProtected(bearerWith(claims -> claims.notBeforeTime(Date.from(future))
                .expirationTime(Date.from(future.plusSeconds(60))))).andExpect(status().isUnauthorized());
    }

    @Test
    void algoritmaKarisikligiReddedilir_noneVeHs256() throws Exception {
        JWTClaimsSet claims = validClaims(newUser()).build();

        callProtected("Bearer " + new PlainJWT(claims).serialize()).andExpect(status().isUnauthorized());

        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .keyID(KEY_ID).type(ACCESS_TOKEN_TYPE).build(), claims);
        hmac.sign(new MACSigner(SIGNING_KEY.toPublicJWK().toRSAPublicKey().getEncoded()));
        callProtected("Bearer " + hmac.serialize()).andExpect(status().isUnauthorized());
    }

    @Test
    void bozulmusImzaReddedilir() throws Exception {
        String token = accessToken(newUser(), UnaryOperator.identity());
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        callProtected("Bearer " + tampered).andExpect(status().isUnauthorized());
    }

    @Test
    void bozukBasliklarReddedilir() throws Exception {
        callProtected("Bearer ").andExpect(status().isUnauthorized());
        callProtected("Bearer abc.def.ghi").andExpect(status().isUnauthorized());
        callProtected("Basic dXNlcjpwYXNz").andExpect(status().isUnauthorized());
    }

    @Test
    void tanimsizYollarKorunur_denyByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/social/unknown")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/other/anything").header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void saglikUcuHerkeseAcik_digerActuatorUclariKapali() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private ResultActions callProtected(String authorization) throws Exception {
        return mockMvc.perform(get(SocialApiPaths.FRIEND_REQUESTS_INCOMING)
                .header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private static String bearerWith(UnaryOperator<JWTClaimsSet.Builder> change) {
        return "Bearer " + accessToken(newUser(), change);
    }
}
