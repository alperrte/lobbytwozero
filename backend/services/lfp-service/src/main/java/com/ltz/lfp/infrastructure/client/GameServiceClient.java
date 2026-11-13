package com.ltz.lfp.infrastructure.client;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ltz.lfp.application.port.CatalogGame;
import com.ltz.lfp.application.port.GameCatalogPort;
import com.ltz.lfp.application.port.GameCatalogUnavailableException;
import com.ltz.lfp.domain.entity.ListingText;
import com.ltz.lfp.infrastructure.client.dto.response.GameCardResponse;
import com.ltz.lfp.infrastructure.config.ServiceClientProperties;

/**
 * game-service'in herkese açık oyun kartı ucu ({@code GET /api/v1/game/{gameId}/card}; docs/api.md → GAME). Uç
 * kimlik istemez ve yalnız yayındaki (ACTIVE) oyunu döner: {@code 404} "oyun yok ya da yayında değil" demektir. Diğer
 * her hata, zaman aşımı ve tutarsız yanıt (başka kimlik, boş ad) "katalog ulaşılamaz" sayılır; oyun var ya da yok diye
 * tahmin yapılmaz. Zaman aşımı ve yönlendirme ayarları {@code spring.http.clients.*} ile gelir; istek otomatik
 * tekrarlanmaz. Yanıttaki ad güvenilmez girdi olarak temizlenir.
 */
public class GameServiceClient implements GameCatalogPort {

    private final RestClient restClient;
    private final String cardPath;

    public GameServiceClient(RestClient.Builder builder, ServiceClientProperties properties) {
        this.restClient = builder.clone().baseUrl(properties.gameServiceUrl()).build();
        this.cardPath = properties.gameCardPath();
    }

    @Override
    public Optional<CatalogGame> find(UUID gameId) {
        GameCardResponse card;
        try {
            card = restClient.get()
                    .uri(cardPath, gameId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GameCardResponse.class);
        } catch (HttpClientErrorException.NotFound _) {
            return Optional.empty();
        } catch (RestClientException exception) {
            // İleti yalnız istisna türünü taşır (yanıt gövdesi yok); neden zincirde kalır.
            throw new GameCatalogUnavailableException("Game card lookup failed: "
                    + exception.getClass().getSimpleName(), exception);
        }
        return Optional.of(toGame(gameId, card));
    }

    private static CatalogGame toGame(UUID requested, GameCardResponse card) {
        if (card == null || !requested.equals(card.gameId())) {
            throw new GameCatalogUnavailableException("Game card response does not match the request.");
        }
        String title = ListingText.gameTitle(card.title());
        if (title == null) {
            throw new GameCatalogUnavailableException("Game card response has no title.");
        }
        return new CatalogGame(requested, title, card.gameType());
    }
}
