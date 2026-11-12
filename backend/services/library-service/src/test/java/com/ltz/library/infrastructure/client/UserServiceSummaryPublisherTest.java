package com.ltz.library.infrastructure.client;

import com.ltz.library.domain.exception.SummaryDeliveryException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** User {@code POST /internal/events} sözleşmesi; sahte HTTP (MockRestServiceServer), gerçek User değildir. */
class UserServiceSummaryPublisherTest {
    private static final String URL = "http://user.test/internal/events";
    private static final String TOKEN = "synthetic.events.token";
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-09T20:00:00.123456789Z");
    private MockRestServiceServer server;
    private AuthServiceTokenClient tokens;
    private UserServiceSummaryPublisher publisher;

    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tokens = Mockito.mock(AuthServiceTokenClient.class);
        when(tokens.configured()).thenReturn(true);
        when(tokens.token("user-service", "events:ingest")).thenReturn(Optional.of(TOKEN));
        publisher = new UserServiceSummaryPublisher(builder, tokens, "http://user.test", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void sendsTheDocumentedEnvelopeWithAbsoluteCountsAndAnEventsIngestToken() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(content().json("{\"eventType\":\"LibrarySummaryChanged\",\"eventVersion\":1,"
                        + "\"occurredAt\":\"2026-11-09T20:00:00.123456Z\",\"producer\":\"library-service\","
                        + "\"aggregateId\":\"" + USER + "\",\"payload\":{\"userId\":\"" + USER
                        + "\",\"collectionCount\":3,\"completedCount\":1,\"favoriteCount\":2}}", JsonCompareMode.LENIENT))
                .andRespond(withSuccess("{\"outcome\":\"PROCESSED\"}", MediaType.APPLICATION_JSON));
        publisher.publish(USER, 3, 1, 2);
        server.verify();
    }

    @Test void everyDeliveryGetsANewEventIdAndOnlyEnvelopeFieldsAreSent() {
        var bodies = new java.util.ArrayList<String>();
        for (int call = 0; call < 2; call++) {
            server.expect(requestTo(URL)).andExpect(request -> bodies.add(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString()))
                    .andRespond(withSuccess());
        }
        publisher.publish(USER, 1, 0, 0);
        publisher.publish(USER, 1, 0, 0);
        server.verify();
        assertFalse(bodies.get(0).equals(bodies.get(1)), "eventId her teslimde farklıdır");
        for (String body : bodies) {
            assertFalse(body.contains(TOKEN), "token gövdeye yazılmaz");
            assertTrue(body.matches(".*\"eventId\":\"[0-9a-f-]{36}\".*"));
        }
    }

    @Test void nonSuccessStatusesRedirectsAndTransportFailuresRaiseADeliveryException() {
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.FOUND)) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withStatus(status));
            assertThrows(SummaryDeliveryException.class, () -> publisher.publish(USER, 1, 0, 0), status.toString());
            server.verify();
        }
        server.reset();
        server.expect(requestTo(URL)).andRespond(withException(new IOException("timeout")));
        assertThrows(SummaryDeliveryException.class, () -> publisher.publish(USER, 1, 0, 0));
        server.verify();
    }

    @Test void errorMessagesNeverCarryTheTokenTheResponseBodyOrTheUser() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("secret-body " + USER).contentType(MediaType.TEXT_PLAIN));
        var error = assertThrows(SummaryDeliveryException.class, () -> publisher.publish(USER, 1, 0, 0));
        assertFalse(error.getMessage().contains("secret-body") || error.getMessage().contains(TOKEN) || error.getMessage().contains(USER.toString()));
    }

    @Test void withoutATokenNothingIsSent() {
        when(tokens.token("user-service", "events:ingest")).thenReturn(Optional.empty());
        server.expect(never(), requestTo(URL)).andRespond(withSuccess());
        assertThrows(SummaryDeliveryException.class, () -> publisher.publish(USER, 1, 0, 0));
        server.verify();
    }

    @Test void readinessNeedsBothAnAddressAndAServiceIdentity() {
        assertTrue(publisher.ready());
        when(tokens.configured()).thenReturn(false);
        assertFalse(publisher.ready());
        when(tokens.configured()).thenReturn(true);
        for (String url : new String[] {null, "", " "}) {
            var unconfigured = new UserServiceSummaryPublisher(RestClient.builder(), tokens, url, Clock.systemUTC());
            assertFalse(unconfigured.ready());
            assertThrows(SummaryDeliveryException.class, () -> unconfigured.publish(USER, 1, 0, 0));
        }
    }
}
