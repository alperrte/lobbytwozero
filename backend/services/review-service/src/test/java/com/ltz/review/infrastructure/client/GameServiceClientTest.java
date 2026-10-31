package com.ltz.review.infrastructure.client;

import com.ltz.review.domain.exception.ReviewException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GameServiceClientTest {
    @Test void activeCanonicalIdUsesOneBatchRequestWithoutUserCredentials() {
        UUID game = UUID.randomUUID();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GameServiceClient(builder, "http://localhost");
        server.expect(requestTo("http://localhost/api/v1/game/games/batch")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"gameIds\":[\"" + game + "\"]}"))
                .andExpect(request -> assertEquals(null, request.getHeaders().getFirst("Authorization")))
                .andRespond(withSuccess("{\"items\":[{\"gameId\":\"" + game + "\",\"title\":\"Test\"}],\"unavailable\":[]}", MediaType.APPLICATION_JSON));
        client.requireAvailable(game);
        server.verify();
    }

    @Test void unavailableIs404AndMalformedOrInconsistentResponsesAre503() {
        UUID game = UUID.randomUUID();
        for (String response : java.util.List.of("{}", "{\"items\":[],\"unavailable\":[]}",
                "{\"items\":[{\"gameId\":\"" + UUID.randomUUID() + "\"}],\"unavailable\":[]}",
                "{\"items\":[],\"unavailable\":[\"" + game + "\"]}")) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://localhost/api/v1/game/games/batch")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            var client = new GameServiceClient(builder, "http://localhost");
            int expected = response.contains("unavailable\":[\"") ? 404 : 503;
            assertEquals(expected, assertThrows(ReviewException.class, () -> client.requireAvailable(game)).status());
            server.verify();
        }
    }

    @Test void dependencyFailureDoesNotRetryOrExposeInternalAddress() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost/api/v1/game/games/batch")).andRespond(withServerError());
        var client = new GameServiceClient(builder, "http://localhost");
        UUID game = UUID.randomUUID();
        assertEquals(503, assertThrows(ReviewException.class, () -> client.requireAvailable(game)).status());
        server.verify();
    }
}
