package com.ltz.review.api.controller;

import com.ltz.review.api.dto.response.GameRatingSummaryResponse;
import com.ltz.review.api.dto.response.RatingSummariesResponse;
import com.ltz.review.api.dto.response.RatingSummaryPageResponse;
import com.ltz.review.api.exception.AccountErasureExceptionHandler;
import com.ltz.review.application.service.RatingSummaryService;
import com.ltz.review.infrastructure.security.AccountErasureSecurityConfiguration;
import com.ltz.review.infrastructure.security.ProductionServiceTokenSupport;
import com.ltz.review.infrastructure.security.ServiceTokenDecoderConfiguration;
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
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Game → Review servis okuması: gerçek imza/JWKS/güvenlik zinciri/controller; DB'siz, servis sahte. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = InternalRatingSummaryTest.WebConfig.class)
@WebAppConfiguration
class InternalRatingSummaryTest {
    static final String PATH = InternalRatingSummaryController.PATH;
    static final KeyPair KEYS = keys();
    static final HttpServer JWKS = jwks();
    static final UUID GAME = UUID.randomUUID();
    @Autowired WebApplicationContext context;
    @Autowired RatingSummaryService summaries;
    MockMvc mvc;

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({InternalRatingSummaryController.class, AccountErasureController.class, AccountErasureExceptionHandler.class,
            ServiceTokenDecoderConfiguration.class, AccountErasureSecurityConfiguration.class})
    static class WebConfig {
        @Bean RatingSummaryService summaries() { return mock(RatingSummaryService.class); }
        @Bean com.ltz.review.application.service.AccountErasureService erasure() {
            return mock(com.ltz.review.application.service.AccountErasureService.class);
        }
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("ltz.security.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clearInvocations(summaries);
        when(summaries.getAll(anyList())).thenAnswer(call -> new RatingSummariesResponse(
                ((List<?>) call.getArgument(0)).stream().map(id -> new GameRatingSummaryResponse((UUID) id, new BigDecimal("7.50"), 2)).toList()));
    }

    @AfterAll static void stop() { JWKS.stop(0); }

    @Test void gameServiceTokenWithRatingsReadScopeReadsSummaries() throws Exception {
        call(post(PATH).contentType("application/json").content(body(GAME)), token(claims()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].gameId").value(GAME.toString()))
                .andExpect(jsonPath("$.items[0].averageRating").value(7.5))
                .andExpect(jsonPath("$.items[0].ratingCount").value(2));
        verify(summaries).getAll(List.of(GAME));
    }

    @Test void emptyListIsAcceptedAndOverFiftyOrInvalidPayloadIsBadRequestWithoutQuery() throws Exception {
        call(post(PATH).contentType("application/json").content("{\"gameIds\":[]}"), token(claims())).andExpect(status().isOk());
        clearInvocations(summaries);
        var tooMany = new StringBuilder("{\"gameIds\":[");
        for (int i = 0; i < 51; i++) tooMany.append(i == 0 ? "" : ",").append('"').append(UUID.randomUUID()).append('"');
        for (String invalid : List.of(tooMany.append("]}").toString(), "{\"gameIds\":[null]}", "{\"gameIds\":[\"x\"]}",
                "{\"gameIds\":null}", "{}", "{\"gameIds\":", "[]")) {
            call(post(PATH).contentType("application/json").content(invalid), token(claims()))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        verifyNoInteractions(summaries);
    }

    @Test void bodyOverSixtyFourKibIsPayloadTooLargeAndNeverReachesService() throws Exception {
        String huge = "{\"gameIds\":[],\"pad\":\"" + "a".repeat(65536) + "\"}";
        call(post(PATH).contentType("application/json").content(huge), token(claims()))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.status").value(413));
        verifyNoInteractions(summaries);
    }

    @Test void anonymousAndUserTokensAreUnauthorized() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(body(GAME)))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        for (String role : List.of("PLAYER", "ADMIN", "MODERATOR")) {
            var header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("at+jwt")).keyID("rating-test").build();
            call(post(PATH).contentType("application/json").content(body(GAME)),
                    sign(claims().audience("ltz-api").subject(UUID.randomUUID().toString()).claim("roles", List.of(role))
                            .claim("scope", "rating-summaries:read"), header)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(summaries);
    }

    @Test void wrongOrMultipleAudiencesAreUnauthorized() throws Exception {
        for (List<String> audience : List.of(List.of("social-service"), List.of("game-service"), List.of("ltz-api"),
                List.of("review-service", "social-service"))) {
            call(post(PATH).contentType("application/json").content(body(GAME)), token(claims().audience(audience)))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(summaries);
    }

    @Test void missingWrongOrBorrowedScopeIsForbidden() throws Exception {
        for (Object scope : Arrays.asList(null, "", "account:erase", "ratings:read", "ratings:write", "rating-summaries", "events:ingest")) {
            call(post(PATH).contentType("application/json").content(body(GAME)),
                    token(claims().claim("scope", scope).claim("roles", List.of("ADMIN")).claim("scp", List.of("rating-summaries:read"))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        }
        verifyNoInteractions(summaries);
    }

    @Test void onlyExactGameServiceSubjectIsAccepted() throws Exception {
        for (String subject : List.of("svc:auth-service", "svc:admin-service", "svc:library-service", "svc:game-service-x",
                "svc:Game-Service", "svc:", "svc:game-service ")) {
            call(request(), token(claims().subject(subject))).andExpect(status().isForbidden());
        }
        // svc: öneki olmayan kimlik zaten servis token'ı sözleşmesini bozar: kimlik doğrulaması düzeyinde reddedilir.
        for (String subject : List.of("game-service", UUID.randomUUID().toString())) {
            call(request(), token(claims().subject(subject))).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(summaries);
    }

    @Test void erasureTokenCannotReadAndRatingsTokenCannotErase() throws Exception {
        UUID account = UUID.randomUUID();
        call(post(PATH).contentType("application/json").content(body(GAME)),
                token(claims().subject("svc:auth-service").claim("scope", "account:erase").claim("account_id", account.toString())))
                .andExpect(status().isForbidden());
        call(delete("/internal/v1/accounts/" + account), token(claims().claim("account_id", account.toString())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(summaries);
    }

    @Test void invalidTimeAndMissingClaimsAreUnauthorized() throws Exception {
        Instant now = Instant.now();
        for (var bad : List.of(
                claims().expirationTime(Date.from(now.plusSeconds(61))),
                claims().expirationTime(Date.from(now.minusSeconds(120))).issueTime(Date.from(now.minusSeconds(150))),
                claims().issueTime(null), claims().notBeforeTime(null), claims().expirationTime(null), claims().jwtID(null),
                claims().issuer("wrong"),
                claims().notBeforeTime(Date.from(now.plusSeconds(120))))) {
            call(post(PATH).contentType("application/json").content(body(GAME)), token(bad)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(summaries);
    }

    @Test void badSignaturesAlgorithmsTypesAndUnknownKeysAreUnauthorized() throws Exception {
        call(request(), sign(claims(), new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("svc+jwt")).keyID("unknown").build()))
                .andExpect(status().isUnauthorized());
        call(request(), sign(claims(), new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("at+jwt")).keyID("rating-test").build()))
                .andExpect(status().isUnauthorized());
        call(request(), new PlainJWT(claims().build()).serialize()).andExpect(status().isUnauthorized());
        var hs = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("svc+jwt")).keyID("rating-test").build(), claims().build());
        hs.sign(new MACSigner(new byte[32]));
        call(request(), hs.serialize()).andExpect(status().isUnauthorized());
        call(request(), token(claims()) + "corrupt").andExpect(status().isUnauthorized());
        verifyNoInteractions(summaries);
    }

    @Test void otherMethodsAndSiblingInternalPathsAreDenied() throws Exception {
        call(delete(PATH), token(claims())).andExpect(status().isForbidden());
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(PATH), token(claims())).andExpect(status().isForbidden());
        call(get(PATH + "/extra"), token(claims())).andExpect(status().isForbidden());
        call(post(PATH + "/extra").contentType("application/json").content(body(GAME)), token(claims())).andExpect(status().isForbidden());
        call(post("/internal/v1/games/other").contentType("application/json").content(body(GAME)), token(claims())).andExpect(status().isForbidden());
        verifyNoInteractions(summaries);
    }

    @Test void serviceTokenCannotEnterUserNamespaceEvenWithRatingsScope() throws Exception {
        call(post("/api/v1/review/games/rating-summaries").contentType("application/json").content(body(GAME)), token(claims()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(summaries);
    }

    @Test void realAuthClientCredentialsTokenIsCompatible(@TempDir java.nio.file.Path output) throws Exception {
        String value = ProductionServiceTokenSupport.issueClientToken(output, KEYS, "game-service", "review-service", Set.of("rating-summaries:read"));
        call(post(PATH).contentType("application/json").content(body(GAME)), value).andExpect(status().isOk());
        verify(summaries).getAll(List.of(GAME));
    }

    @Test void realAuthTokensWithWrongClientOrScopeOrAudienceAreRejected(@TempDir java.nio.file.Path output) throws Exception {
        call(request(), ProductionServiceTokenSupport.issueClientToken(output, KEYS, "library-service", "review-service", Set.of("rating-summaries:read")))
                .andExpect(status().isForbidden());
        call(request(), ProductionServiceTokenSupport.issueClientToken(output, KEYS, "game-service", "review-service", Set.of("events:ingest")))
                .andExpect(status().isForbidden());
        call(request(), ProductionServiceTokenSupport.issueClientToken(output, KEYS, "game-service", "user-service", Set.of("rating-summaries:read")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(summaries);
    }

    @Test void exportPageReadsWithCursorAndDefaultLimit() throws Exception {
        UUID cursor = UUID.randomUUID();
        when(summaries.page(any(), anyInt())).thenReturn(new RatingSummaryPageResponse(
                List.of(new GameRatingSummaryResponse(GAME, new BigDecimal("7.50"), 2)), GAME));
        call(get(PATH).param("after", cursor.toString()).param("limit", "3"), token(claims()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.items[0].gameId").value(GAME.toString()))
                .andExpect(jsonPath("$.items[0].ratingCount").value(2))
                .andExpect(jsonPath("$.nextAfter").value(GAME.toString()));
        verify(summaries).page(cursor, 3);
        clearInvocations(summaries);
        call(get(PATH), token(claims())).andExpect(status().isOk());
        verify(summaries).page(null, 200);
    }

    @Test void exportPageRejectsInvalidParametersWithoutQuery() throws Exception {
        for (String[] bad : new String[][]{{"limit", "0"}, {"limit", "501"}, {"limit", "-1"}, {"limit", "x"}, {"after", "not-a-uuid"}}) {
            call(get(PATH).param(bad[0], bad[1]), token(claims())).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        verifyNoInteractions(summaries);
    }

    @Test void exportPageRequiresTheSameIdentityScopeAndSubject() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        call(get(PATH), token(claims().claim("scope", "account:erase"))).andExpect(status().isForbidden());
        call(get(PATH), token(claims().subject("svc:library-service"))).andExpect(status().isForbidden());
        call(get(PATH), token(claims().audience("user-service"))).andExpect(status().isUnauthorized());
        var user = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("at+jwt")).keyID("rating-test").build();
        call(get(PATH), sign(claims().audience("ltz-api").subject(UUID.randomUUID().toString()).claim("roles", List.of("ADMIN")), user))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(summaries);
    }

    @Test void guardConstantsMatchTheDocumentedContract() {
        assertEquals("/internal/v1/games/rating-summaries", InternalRatingSummaryController.PATH);
        assertEquals("rating-summaries:read", InternalRatingSummaryController.SCOPE);
        assertEquals("svc:game-service", InternalRatingSummaryController.CALLER);
        assertEquals(500, InternalRatingSummaryController.MAX_PAGE);
    }

    static MockHttpServletRequestBuilder request() {
        return post(PATH).contentType("application/json").content(body(GAME));
    }

    static String body(UUID... ids) {
        return "{\"gameIds\":[" + String.join(",", Arrays.stream(ids).map(id -> "\"" + id + "\"").toList()) + "]}";
    }

    ResultActions call(MockHttpServletRequestBuilder request, String value) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + value));
    }

    static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("ltz-auth").audience("review-service").subject("svc:game-service")
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(30)))
                .jwtID(UUID.randomUUID().toString()).claim("scope", "rating-summaries:read");
    }

    static String token(JWTClaimsSet.Builder claims) throws Exception {
        return sign(claims, new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("svc+jwt")).keyID("rating-test").build());
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
            var key = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) KEYS.getPublic()).keyID("rating-test").build();
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
