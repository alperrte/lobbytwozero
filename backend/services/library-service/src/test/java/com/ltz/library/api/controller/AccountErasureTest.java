package com.ltz.library.api.controller;

import com.ltz.library.api.exception.AccountErasureExceptionHandler;
import com.ltz.library.application.service.AccountErasureService;
import com.ltz.library.infrastructure.security.AccountErasureSecurityConfiguration;
import com.ltz.library.infrastructure.security.ServiceTokenDecoderConfiguration;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gerçek imza/JWKS/filtre/controller/servis; DB'siz iskeletin HTTP sözleşmesi. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AccountErasureTest.WebConfig.class)
@WebAppConfiguration
class AccountErasureTest {
    static final UUID ACCOUNT = UUID.randomUUID();
    static final KeyPair KEYS = keys();
    static final HttpServer JWKS = jwks();
    static final String PATH = "/internal/v1/accounts/" + ACCOUNT;
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({AccountErasureController.class, AccountErasureService.class, AccountErasureExceptionHandler.class,
            ServiceTokenDecoderConfiguration.class, AccountErasureSecurityConfiguration.class})
    static class WebConfig {}

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("ltz.security.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @AfterAll static void stop() { JWKS.stop(0); }

    @Test void emptyAccountAndRepeatedDeletionAreNoContent() throws Exception {
        for (int i = 0; i < 2; i++) call(delete(PATH), token(claims())).andExpect(status().isNoContent()).andExpect(content().string(""));
        call(delete("/internal/v1/accounts/" + UUID.randomUUID()), token(claims().claim("account_id", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden()); // farklı hesabı silmez
        UUID another = UUID.randomUUID();
        call(delete("/internal/v1/accounts/" + another), token(claims().claim("account_id", another.toString())))
                .andExpect(status().isNoContent());
    }

    @Test void anonymousAndUserTokensIncludingAdminAreUnauthorized() throws Exception {
        mvc.perform(delete(PATH)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        for (String role : List.of("PLAYER", "ADMIN")) {
            var header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("at+jwt")).keyID("erasure-test").build();
            call(delete(PATH), sign(claims().audience("ltz-api").subject(ACCOUNT.toString()).claim("roles", List.of(role)), header))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test void wrongOrMultipleAudiencesAreUnauthorized() throws Exception {
        call(delete(PATH), token(claims().audience("social-service"))).andExpect(status().isUnauthorized());
        call(delete(PATH), token(claims().audience(List.of("library-service", "social-service")))).andExpect(status().isUnauthorized());
    }

    @Test void missingOrWrongScopeCannotBorrowRoles() throws Exception {
        for (Object scope : Arrays.asList(null, "other:scope")) {
            call(delete(PATH), token(claims().claim("scope", scope).claim("roles", List.of("ADMIN")).claim("scp", List.of("account:erase"))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        }
    }

    @Test void onlyExactAuthSubjectCanErase() throws Exception {
        call(delete(PATH), token(claims().subject("svc:admin-service")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test void missingWrongOrMalformedAccountBindingIsForbidden() throws Exception {
        for (Object value : Arrays.asList(null, UUID.randomUUID().toString(), 42)) {
            call(delete(PATH), token(claims().claim("account_id", value))).andExpect(status().isForbidden());
        }
    }

    @Test void invalidTimeAndMissingClaimsAreUnauthorized() throws Exception {
        Instant now = Instant.now();
        for (var bad : List.of(
                claims().expirationTime(Date.from(now.plusSeconds(61))),
                claims().expirationTime(Date.from(now.minusSeconds(120))).issueTime(Date.from(now.minusSeconds(150))),
                claims().issueTime(null), claims().notBeforeTime(null), claims().expirationTime(null), claims().jwtID(null),
                claims().issueTime(Date.from(now.plusSeconds(120))).expirationTime(Date.from(now.plusSeconds(150))),
                claims().notBeforeTime(Date.from(now.plusSeconds(120))))) {
            call(delete(PATH), token(bad)).andExpect(status().isUnauthorized());
        }
    }

    @Test void badSignaturesAlgorithmsAndUnknownKeysAreUnauthorized() throws Exception {
        call(delete(PATH), sign(claims(), new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("svc+jwt")).keyID("unknown").build()))
                .andExpect(status().isUnauthorized());
        var unsigned = new PlainJWT(claims().build()).serialize();
        call(delete(PATH), unsigned).andExpect(status().isUnauthorized());
        var hs = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("svc+jwt")).keyID("erasure-test").build(), claims().build());
        hs.sign(new MACSigner(new byte[32]));
        call(delete(PATH), hs.serialize()).andExpect(status().isUnauthorized());
        call(delete(PATH), token(claims()) + "corrupt").andExpect(status().isUnauthorized());
    }

    @Test void serviceTokenCannotEnterPublicNamespace() throws Exception {
        call(get("/api/v1/library/probe"), token(claims())).andExpect(status().isUnauthorized());
    }

    @Test void otherInternalMethodsAndPathsAreDenied() throws Exception {
        call(get(PATH), token(claims())).andExpect(status().isForbidden());
        call(delete("/internal/v1/other"), token(claims())).andExpect(status().isForbidden());
    }

    @Test void malformedPathIsBadRequest() throws Exception {
        call(delete("/internal/v1/accounts/not-a-uuid"), token(claims())).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test void actuatorIsPublicAndNoBusinessRoutesWereOpened() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound()); // bu dar MVC bağlamında Actuator yok
        call(get("/unknown"), token(claims())).andExpect(status().isUnauthorized());
    }

    @Test void skeletonServiceHasNoAccountState() {
        var service = new AccountErasureService();
        service.erase(ACCOUNT);
        service.erase(UUID.randomUUID());
        assertThrows(NullPointerException.class, () -> service.erase(null));
    }

    @Test void realAuthServiceTokenIssuerIsCompatible(@org.junit.jupiter.api.io.TempDir java.nio.file.Path output) throws Exception {
        String value = com.ltz.library.infrastructure.security.ProductionServiceTokenSupport.issue(output, KEYS, "library-service", ACCOUNT);
        call(delete(PATH), value).andExpect(status().isNoContent());
    }

    ResultActions call(MockHttpServletRequestBuilder request, String value) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + value));
    }

    static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("ltz-auth").audience("library-service").subject("svc:auth-service")
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(30)))
                .jwtID(UUID.randomUUID().toString()).claim("scope", "account:erase").claim("account_id", ACCOUNT.toString());
    }

    static String token(JWTClaimsSet.Builder claims) throws Exception {
        return sign(claims, new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("svc+jwt")).keyID("erasure-test").build());
    }

    static String sign(JWTClaimsSet.Builder claims, JWSHeader header) throws Exception {
        var jwt = new SignedJWT(header, claims.build());
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }

    static KeyPair keys() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072); return generator.generateKeyPair(); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    static HttpServer jwks() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var key = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) KEYS.getPublic()).keyID("erasure-test").build();
            var issuerKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) KEYS.getPublic()).keyIDFromThumbprint().build();
            byte[] body = new JWKSet(List.of(key, issuerKey)).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
