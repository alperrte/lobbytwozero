package com.ltz.library.infrastructure.client;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Auth {@code POST /internal/v1/service-tokens} sözleşmesi; sahte HTTP (MockRestServiceServer), gerçek Auth değildir. */
class AuthServiceTokenClientTest {
    private static final String PATH = "/internal/v1/service-tokens";
    private static final String URL = "http://auth.test" + PATH;
    private static final String SECRET = "synthetic-secret-for-tests-only-0123456789";
    private static final String TOKEN = "synthetic.token.value";
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-30T12:00:00Z"));
    private MockRestServiceServer server;
    private AuthServiceTokenClient client;

    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AuthServiceTokenClient(builder, "http://auth.test", PATH, "library-service", SECRET, clock);
    }

    @Test void requestsTheDocumentedClientCredentialsBodyAndReturnsABearerToken() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(header("Content-Type", "application/json"))
                .andExpect(content().json("{\"clientId\":\"library-service\",\"clientSecret\":\"" + SECRET + "\",\"audience\":\"user-service\"}"))
                .andRespond(ok(60, "privacy:read"));
        assertEquals(TOKEN, client.token("user-service", "privacy:read").orElseThrow());
        server.verify();
    }

    @Test void tokenIsReusedOnlyBeforeItsRenewalMarginAndIsNotKeptAfterwards() {
        server.expect(once(), requestTo(URL)).andRespond(ok(60, "privacy:read"));
        client.token("user-service", "privacy:read");
        clock.advance(49); // 60 sn ömür − 10 sn pay = 50 sn
        client.token("user-service", "privacy:read");
        server.verify();
        server.reset();
        server.expect(once(), requestTo(URL)).andRespond(ok(60, "privacy:read"));
        clock.advance(2);
        assertTrue(client.token("user-service", "privacy:read").isPresent());
        server.verify();
    }

    @Test void audiencesDoNotShareTokens() {
        server.expect(once(), requestTo(URL)).andExpect(content().string(org.hamcrest.Matchers.containsString("\"audience\":\"user-service\"")))
                .andRespond(ok(60, "privacy:read"));
        server.expect(once(), requestTo(URL)).andExpect(content().string(org.hamcrest.Matchers.containsString("\"audience\":\"social-service\"")))
                .andRespond(ok(60, "privacy:read"));
        client.token("user-service", "privacy:read");
        client.token("social-service", "privacy:read");
        server.verify();
    }

    @Test void privacyReadIsMandatoryAndEventsIngestOrTheOldScopeNeverSubstituteForIt() {
        for (String scope : List.of("events:ingest", "access:decide", "access:decide events:ingest", "signals:read", "account:erase",
                "privacy:write", "privacy", "")) {
            server.reset();
            server.expect(once(), requestTo(URL)).andRespond(ok(60, scope));
            assertFalse(client.privacyDecisionToken().isPresent(), scope);
            server.verify();
        }
    }

    @Test void tokenCarryingPrivacyReadIsAcceptedWithOrWithoutEventsIngestInAnyOrder() {
        for (String scope : List.of("privacy:read", "events:ingest privacy:read", "privacy:read events:ingest")) {
            server.reset();
            clock.advance(3600); // önceki başarılı token önbellekte kalmasın
            server.expect(once(), requestTo(URL)).andExpect(content().json("{\"audience\":\"user-service\"}"))
                    .andRespond(ok(60, scope));
            assertEquals(TOKEN, client.privacyDecisionToken().orElseThrow(), scope);
            server.verify();
        }
    }

    @Test void unexpectedStatusBodyScopeOrLifetimeFailClosedAndFailuresAreNotCached() {
        for (var response : List.of(
                withStatus(HttpStatus.UNAUTHORIZED), withStatus(HttpStatus.FORBIDDEN), withStatus(HttpStatus.TOO_MANY_REQUESTS),
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR), withStatus(HttpStatus.SERVICE_UNAVAILABLE), withStatus(HttpStatus.FOUND),
                withSuccess("", MediaType.APPLICATION_JSON), withSuccess("null", MediaType.APPLICATION_JSON),
                withSuccess("not json", MediaType.APPLICATION_JSON), withSuccess("{}", MediaType.APPLICATION_JSON),
                body("{\"accessToken\":\"\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"privacy:read\"}"),
                body("{\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"privacy:read\"}"),
                body("{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"MAC\",\"expiresIn\":60,\"scope\":\"privacy:read\"}"),
                body("{\"accessToken\":\"" + TOKEN + "\",\"expiresIn\":60,\"scope\":\"privacy:read\"}"),
                body("{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"Bearer\",\"scope\":\"privacy:read\"}"),
                body("{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"Bearer\",\"expiresIn\":0,\"scope\":\"privacy:read\"}"),
                body("{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"Bearer\",\"expiresIn\":61,\"scope\":\"privacy:read\"}"),
                body("{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60}"),
                body("{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"events:ingest\"}"),
                body("{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"privacy:read-all\"}"),
                withException(new IOException("test timeout")))) {
            server.reset();
            server.expect(once(), requestTo(URL)).andRespond(response);
            assertFalse(client.token("user-service", "privacy:read").isPresent());
            server.verify();
        }
        // Başarısızlık önbelleğe girmez: sıradaki çağrı yeniden dener.
        server.reset();
        server.expect(once(), requestTo(URL)).andRespond(ok(60, "privacy:read"));
        assertTrue(client.token("user-service", "privacy:read").isPresent());
        server.verify();
    }

    @Test void unconfiguredClientNeverCallsAuth() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        for (var unconfigured : List.of(new AuthServiceTokenClient(builder, "http://auth.test", PATH, "library-service", "", clock),
                new AuthServiceTokenClient(builder, "http://auth.test", PATH, "library-service", "   ", clock),
                new AuthServiceTokenClient(builder, "http://auth.test", PATH, "library-service", null, clock),
                new AuthServiceTokenClient(builder, "http://auth.test", PATH, " ", SECRET, clock),
                new AuthServiceTokenClient(builder, "", PATH, "library-service", SECRET, clock))) {
            assertFalse(unconfigured.configured());
            assertFalse(unconfigured.token("user-service", "privacy:read").isPresent());
        }
        server.verify();
    }

    @Test void invalidAudienceOrScopeArgumentsNeverReachAuth() {
        for (String audience : new String[]{null, "", "User-Service", "user service", "user/service", "../auth", "x".repeat(33)})
            assertFalse(client.token(audience, "privacy:read").isPresent());
        assertFalse(client.token("user-service", null).isPresent());
        server.verify(); // hiç beklenti yok → hiç istek yapılmadı
    }

    @Test void secretAndTokenNeverAppearInStringForms() throws Exception {
        server.expect(requestTo(URL)).andRespond(ok(60, "privacy:read"));
        client.token("user-service", "privacy:read");
        var request = new com.ltz.library.infrastructure.client.dto.request.ServiceTokenRequest("library-service", SECRET, "user-service");
        var response = new com.ltz.library.infrastructure.client.dto.response.ServiceTokenResponse(TOKEN, "Bearer", 60L, "privacy:read");
        for (String text : List.of(request.toString(), response.toString(), client.toString())) {
            assertFalse(text.contains(SECRET), text);
            assertFalse(text.contains(TOKEN), text);
        }
        var cached = AuthServiceTokenClient.class.getDeclaredField("cache");
        cached.setAccessible(true);
        assertFalse(cached.get(client).toString().contains(TOKEN));
    }

    private static org.springframework.test.web.client.ResponseCreator ok(long expiresIn, String scope) {
        return body("{\"accessToken\":\"" + TOKEN + "\",\"tokenType\":\"Bearer\",\"expiresIn\":" + expiresIn + ",\"scope\":\"" + scope + "\"}");
    }

    private static org.springframework.test.web.client.ResponseCreator body(String json) {
        return withSuccess(json, MediaType.APPLICATION_JSON);
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
