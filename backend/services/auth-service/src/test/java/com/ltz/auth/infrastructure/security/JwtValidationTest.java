package com.ltz.auth.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.api.controller.AuthApiPaths;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/** Access token doğrulamasının saldırı senaryoları: her biri korumalı uçta 401 almalıdır. */
@Import(RecordingMailPortConfiguration.class)
class JwtValidationTest extends IntegrationTestSupport {

    private static final int ATTACKER_KEY_BITS = 2048;
    private static final String PROTECTED = AuthApiPaths.TOTP_SETUP;

    @Test
    void gecerliTokenBearerBasligiylaKabulEdilirCsrfGerekmez() throws Exception {
        String token = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername())).access().getValue();

        callProtected(token).andExpect(status().isOk());
    }

    @Test
    void baskaAnahtarlaImzalanmisTokenReddedilirRolEnjeksiyonuIseYaramaz() throws Exception {
        SignedJWT original = realToken();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(ATTACKER_KEY_BITS);
        String forged = sign((RSAPrivateKey) generator.generateKeyPair().getPrivate(), original,
                claims -> claims.claim(TokenClaims.ROLES, List.of("ADMIN")));

        callProtected(forged).andExpect(status().isUnauthorized());
    }

    @Test
    void yanlisAudienceIssuerVeTurReddedilir() throws Exception {
        SignedJWT original = realToken();
        RSAPrivateKey serverKey = (RSAPrivateKey) TEST_JWT_KEYS.getPrivate();

        callProtected(sign(serverKey, original, claims -> claims.audience("other-api"))).andExpect(status().isUnauthorized());
        callProtected(sign(serverKey, original, claims -> claims.issuer("evil-issuer"))).andExpect(status().isUnauthorized());
        callProtected(sign(serverKey, original, JOSEObjectType.JWT, UnaryOperator.identity()))
                .andExpect(status().isUnauthorized());
        callProtected(sign(serverKey, original, claims -> claims.claim(TokenClaims.SESSION_ID, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suresiDolmusTokenReddedilir() throws Exception {
        SignedJWT original = realToken();
        Instant past = Instant.now().minusSeconds(600);

        callProtected(sign((RSAPrivateKey) TEST_JWT_KEYS.getPrivate(), original,
                claims -> claims.issueTime(Date.from(past)).notBeforeTime(Date.from(past))
                        .expirationTime(Date.from(past.plusSeconds(60)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void algoritmaKarisikligiReddedilirNoneVeHs256() throws Exception {
        SignedJWT original = realToken();
        JWTClaimsSet claims = original.getJWTClaimsSet();

        callProtected(new PlainJWT(claims).serialize()).andExpect(status().isUnauthorized());

        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .keyID(original.getHeader().getKeyID()).type(original.getHeader().getType()).build(), claims);
        hmac.sign(new MACSigner(TEST_JWT_KEYS.getPublic().getEncoded()));
        callProtected(hmac.serialize()).andExpect(status().isUnauthorized());
    }

    @Test
    void bozulmusImzaReddedilir() throws Exception {
        String token = realToken().serialize();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        callProtected(tampered).andExpect(status().isUnauthorized());
    }

    @Test
    void tokensizIstek401GecerliTokenleIzinVerilmeyenUc403() throws Exception {
        String token = realToken().serialize();

        mockMvc.perform(post(PROTECTED).with(xsrf())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(AuthApiPaths.BASE + "/admin/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void jwksYalnizAcikAnahtariYayinlar() throws Exception {
        String keyId = realToken().getHeader().getKeyID();

        mockMvc.perform(get(AuthApiPaths.JWKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].kid").value(keyId))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }

    private SignedJWT realToken() throws Exception {
        return SignedJWT.parse(loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername())).access().getValue());
    }

    private ResultActions callProtected(String token) throws Exception {
        return mockMvc.perform(post(PROTECTED).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String sign(RSAPrivateKey key, SignedJWT original, UnaryOperator<JWTClaimsSet.Builder> change)
            throws Exception {
        return sign(key, original, original.getHeader().getType(), change);
    }

    private static String sign(RSAPrivateKey key, SignedJWT original, JOSEObjectType type,
                               UnaryOperator<JWTClaimsSet.Builder> change) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(original.getHeader().getKeyID()).type(type).build();
        SignedJWT jwt = new SignedJWT(header, change.apply(new JWTClaimsSet.Builder(original.getJWTClaimsSet())).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
