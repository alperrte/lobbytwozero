package com.ltz.library.infrastructure.client;

import com.ltz.library.domain.exception.CatalogUnavailableException;
import com.ltz.library.domain.exception.GameUnavailableException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GameServiceClientTest {
    private static final UUID GAME = UUID.randomUUID();
    private MockRestServiceServer server;
    private GameServiceClient client;

    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GameServiceClient(builder, "http://catalog.test");
    }

    @Test void verifiesCanonicalIdWithoutForwardingCredentials() {
        server.expect(requestTo("http://catalog.test/api/v1/game/games/batch"))
                .andExpect(method(HttpMethod.POST)).andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().json("{\"gameIds\":[\"" + GAME + "\"]}"))
                .andRespond(withSuccess("{\"items\":[{\"gameId\":\"" + GAME + "\",\"title\":\"Test\"}],\"unavailable\":[]}", MediaType.APPLICATION_JSON));
        client.requireAvailable(GAME);
        server.verify();
    }

    @Test void unavailableGamesAreRejected() {
        respond("{\"items\":[],\"unavailable\":[\"" + GAME + "\"]}");
        assertThrows(GameUnavailableException.class, () -> client.requireAvailable(GAME));
        server.verify();
    }

    @Test void malformedOrContradictoryResponsesFailClosed() {
        for (String body : java.util.List.of("null", "{}", "{\"items\":[],\"unavailable\":[]}",
                "{\"items\":[null],\"unavailable\":[]}", "{\"items\":[{}],\"unavailable\":[]}",
                "{\"items\":[{\"gameId\":\"" + GAME + "\"}],\"unavailable\":[\"" + GAME + "\"]}",
                "{\"items\":[{\"gameId\":\"" + UUID.randomUUID() + "\"}],\"unavailable\":[]}", "invalid")) {
            server.reset();
            respond(body);
            assertThrows(CatalogUnavailableException.class, () -> client.requireAvailable(GAME));
            server.verify();
        }
    }

    @Test void outagesTimeoutsAndRateLimitsAreNotMissingGamesOrRetried() {
        for (HttpStatus status : java.util.List.of(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED, HttpStatus.FOUND)) {
            server.reset();
            server.expect(requestTo("http://catalog.test/api/v1/game/games/batch")).andRespond(withStatus(status));
            assertThrows(CatalogUnavailableException.class, () -> client.requireAvailable(GAME));
            server.verify();
        }
        server.reset();
        server.expect(requestTo("http://catalog.test/api/v1/game/games/batch")).andRespond(withException(new IOException("test timeout")));
        assertThrows(CatalogUnavailableException.class, () -> client.requireAvailable(GAME));
        server.verify();
    }

    private void respond(String body) {
        server.expect(requestTo("http://catalog.test/api/v1/game/games/batch")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
