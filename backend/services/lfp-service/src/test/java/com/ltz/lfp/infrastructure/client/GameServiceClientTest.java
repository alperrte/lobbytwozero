package com.ltz.lfp.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.lfp.application.port.CatalogGame;
import com.ltz.lfp.application.port.GameCatalogUnavailableException;
import com.ltz.lfp.infrastructure.config.ServiceClientProperties;

/** game-service oyun kartı: istek biçimi, 404 = katalogda yok, diğer her hata ve tutarsız yanıt fail-closed. */
class GameServiceClientTest {

    private final UUID gameId = UUID.randomUUID();

    private MockRestServiceServer server;
    private GameServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GameServiceClient(builder,
                new ServiceClientProperties("http://game-service:8080", "/api/v1/game/{gameId}/card",
                        "http://auth-service:8080", "/internal/v1/service-tokens", "lfp-service", null,
                        "http://social-service:8080", "/internal/v1/blocks/check"));
    }

    private String cardUrl() {
        return "http://game-service:8080/api/v1/game/" + gameId + "/card";
    }

    private String card(UUID id, String title, String type) {
        String titleJson = title == null ? "null" : "\"" + title + "\"";
        return """
                {"gameId":"%s","title":%s,"origin":"EXTERNAL","metadataCompleteness":"FULL","gameType":"%s",
                 "externalReferences":[{"provider":"STEAM","externalId":"570","lastSyncedAt":null}],
                 "coverUrl":"https://example.test/c.jpg","newField":{"a":1}}
                """.formatted(id, titleJson, type);
    }

    @Test
    void kimliksizGetIleKartiOkurBilinmeyenAlanlarYokSayilir() {
        server.expect(once(), requestTo(cardUrl()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION))
                        .isFalse())
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(card(gameId, "Dota 2", "MAIN_GAME"), MediaType.APPLICATION_JSON));

        Optional<CatalogGame> game = client.find(gameId);

        assertThat(game).contains(new CatalogGame(gameId, "Dota 2", "MAIN_GAME"));
        server.verify();
    }

    @Test
    void notFoundKatalogdaYokDemektir() {
        server.expect(once(), requestTo(cardUrl())).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.find(gameId)).isEmpty();
    }

    @Test
    void sunucuHatasiVeDigerDurumlarUlasilamazSayilir() {
        for (HttpStatus status : new HttpStatus[] {HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_REQUEST,
                HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE}) {
            server.reset();
            server.expect(once(), requestTo(cardUrl())).andRespond(withStatus(status));
            assertThatThrownBy(() -> client.find(gameId)).isInstanceOf(GameCatalogUnavailableException.class);
        }
        server.reset();
        server.expect(once(), requestTo(cardUrl())).andRespond(withServerError());
        assertThatThrownBy(() -> client.find(gameId)).isInstanceOf(GameCatalogUnavailableException.class);
    }

    @Test
    void baskaOyununKartiVeyaBosAdUlasilamazSayilir() {
        server.expect(once(), requestTo(cardUrl()))
                .andRespond(withSuccess(card(UUID.randomUUID(), "Other", "MAIN_GAME"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.find(gameId)).isInstanceOf(GameCatalogUnavailableException.class);

        server.reset();
        server.expect(once(), requestTo(cardUrl()))
                .andRespond(withSuccess(card(gameId, "   ", "MAIN_GAME"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.find(gameId)).isInstanceOf(GameCatalogUnavailableException.class);

        server.reset();
        server.expect(once(), requestTo(cardUrl()))
                .andRespond(withSuccess(card(gameId, null, "MAIN_GAME"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.find(gameId)).isInstanceOf(GameCatalogUnavailableException.class);
    }

    @Test
    void bozukGovdeUlasilamazSayilir() {
        server.expect(once(), requestTo(cardUrl())).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.find(gameId)).isInstanceOf(GameCatalogUnavailableException.class);
    }

    @Test
    void oyunAdiGuvenilmezGirdiOlarakTemizlenir() {
        server.expect(once(), requestTo(cardUrl()))
                .andRespond(withSuccess(card(gameId, "  Half\\nLife\\u0000 2  ", "MAIN_GAME"),
                        MediaType.APPLICATION_JSON));

        assertThat(client.find(gameId)).map(CatalogGame::title).contains("Half Life  2");
    }
}
