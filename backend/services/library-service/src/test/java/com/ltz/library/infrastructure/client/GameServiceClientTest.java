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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test void fiftySummariesAreReadWithOneHttpRequestAndNoCredentials() {
        var ids = java.util.stream.IntStream.range(0, 50).mapToObj(i -> UUID.randomUUID()).toList();
        String requested = ids.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        String cards = ids.stream().map(id -> "{\"gameId\":\"" + id + "\",\"title\":\"Example\"}")
                .collect(java.util.stream.Collectors.joining(","));
        server.expect(requestTo("http://catalog.test/api/v1/game/games/batch")).andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Authorization")).andExpect(content().json("{\"gameIds\":[" + requested + "]}"))
                .andRespond(withSuccess("{\"items\":[" + cards + "],\"unavailable\":[]}", MediaType.APPLICATION_JSON));
        assertEquals(50, client.summaries(ids).size());
        server.verify();
    }

    @Test void mapsKnownMetadataAndConfirmedUnavailableWithoutInventingMissingFields() {
        UUID other = UUID.randomUUID();
        respond("{\"items\":[{\"gameId\":\"" + GAME + "\",\"title\":\" Example \",\"coverUrl\":\"https://cdn.example.test/cover.jpg\","
                + "\"origin\":\"EXTERNAL\",\"metadataCompleteness\":\"PARTIAL\",\"gameType\":\"DLC\",\"externalReferences\":[]}],\"unavailable\":[\"" + other + "\"]}");
        var result = client.summaries(java.util.List.of(GAME, other));
        assertEquals(1, result.size());
        assertEquals("Example", result.get(GAME).title());
        assertEquals("https://cdn.example.test/cover.jpg", result.get(GAME).coverUrl());
        assertEquals("EXTERNAL", result.get(GAME).origin());
        assertEquals("PARTIAL", result.get(GAME).metadataCompleteness());
        assertEquals("DLC", result.get(GAME).gameType());
        server.verify();
    }

    @Test void malformedPartitionsDuplicatesAndUnrequestedIdsFailClosed() {
        UUID other = UUID.randomUUID();
        for (String body : java.util.List.of(
                "{\"items\":[{\"gameId\":\"" + GAME + "\"},{\"gameId\":\"" + GAME + "\"}],\"unavailable\":[\"" + other + "\"]}",
                "{\"items\":[{\"gameId\":\"" + GAME + "\"}],\"unavailable\":[\"" + GAME + "\",\"" + other + "\"]}",
                "{\"items\":[],\"unavailable\":[\"" + GAME + "\",\"" + GAME + "\"]}",
                "{\"items\":[],\"unavailable\":[null]}",
                "{\"items\":[{\"gameId\":\"" + UUID.randomUUID() + "\"}],\"unavailable\":[\"" + other + "\"]}",
                "{\"items\":[{\"gameId\":\"" + GAME + "\"}],\"unavailable\":[]}")) {
            server.reset(); respond(body);
            assertThrows(CatalogUnavailableException.class, () -> client.summaries(java.util.List.of(GAME, other)));
            server.verify();
        }
    }

    @Test void unsafeOptionalMetadataIsOmittedAndBatchLimitsAreChecked() {
        for (String url : java.util.List.of("http://cdn.example.test/x", "https://user@cdn.example.test/x", "https://[", "x".repeat(2049))) {
            server.reset();
            respond("{\"items\":[{\"gameId\":\"" + GAME + "\",\"title\":\"" + "x".repeat(257) + "\",\"coverUrl\":\"" + url
                    + "\",\"origin\":\"OTHER\",\"metadataCompleteness\":\"OTHER\",\"gameType\":\"OTHER\"}],\"unavailable\":[]}");
            var result = client.summaries(java.util.List.of(GAME)).get(GAME);
            assertNull(result.title()); assertNull(result.coverUrl()); assertNull(result.origin());
            assertNull(result.metadataCompleteness()); assertNull(result.gameType());
            server.verify();
        }
        server.reset();
        assertEquals(java.util.Map.of(), client.summaries(java.util.List.of()));
        var tooMany = java.util.stream.IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID()).toList();
        assertThrows(IllegalArgumentException.class, () -> client.summaries(tooMany));
        var withNull = java.util.Arrays.asList(GAME, null);
        assertThrows(IllegalArgumentException.class, () -> client.summaries(withNull));
        respond("{\"items\":[{\"gameId\":\"" + GAME + "\",\"title\":\"   \"}],\"unavailable\":[]}");
        assertNull(client.summaries(java.util.List.of(GAME, GAME)).get(GAME).title());
        server.verify();
    }

    private void respond(String body) {
        server.expect(requestTo("http://catalog.test/api/v1/game/games/batch")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
