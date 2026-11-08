package com.ltz.game.infrastructure.client;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.service.CatalogImportService;
import com.ltz.game.application.service.DiscoveryQuery;
import com.ltz.game.application.service.GameDiscoveryService;
import com.ltz.game.application.service.GameRatingSyncService;
import com.ltz.game.application.service.GameRatingSyncService.Outcome;
import com.ltz.game.domain.enums.CatalogDirection;
import com.ltz.game.domain.enums.CatalogSort;
import com.ltz.game.domain.enums.RatingStatus;
import com.ltz.game.infrastructure.client.FakeServer.Reply;
import com.ltz.game.infrastructure.config.GameRatingConfiguration;
import com.ltz.game.infrastructure.config.GameRatingProperties;
import com.ltz.game.infrastructure.persistence.repository.GameRatingProjectionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gerçek AuthServiceTokenClient + ReviewRatingClient + tarama servisi + PostgreSQL; yalnız Auth ve Review sahte HTTP
 * sunucularıdır. Gerçek Auth token'ı ve gerçek Review ile uçtan uca doğrulama değildir (canlı entegrasyon ayrıca yapılır).
 */
@SpringBootTest
class RatingSyncEndToEndTest extends PostgresTestSupport {
    private static final String SECRET = "test-only-secret-value";
    private static final String TOKEN_BODY = "{\"accessToken\":\"svc-token\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"rating-summaries:read\"}";
    @Autowired JdbcTemplate jdbc;
    @Autowired GameRatingProjectionRepository repository;
    @Autowired GameDiscoveryService discovery;
    FakeServer auth;
    FakeServer review;

    @BeforeEach void setup() throws Exception {
        clean();
        auth = new FakeServer();
        review = new FakeServer();
    }

    @AfterEach void close() {
        auth.close();
        review.close();
        clean();
    }

    void clean() {
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        jdbc.update("""
                update game_rating_sync_state set scan_generation = 0, lease_owner = null, lease_until = null,
                    last_attempt_at = null, last_completed_scan = null, last_completed_at = null, last_error_code = null""");
    }

    UUID game(String title, int suffix) {
        UUID id = UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,game_type) values (?,?,?,now(),now(),'MAIN_GAME')",
                id, title, CatalogImportService.normalize(title));
        return id;
    }

    GameRatingSyncService service(String secret) {
        var properties = new GameRatingProperties(review.baseUrl(), auth.baseUrl(), "/internal/v1/service-tokens", "game-service", secret,
                "review-service", Duration.ofSeconds(1), Duration.ofSeconds(2), 2,
                new GameRatingProperties.Sync(true, Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofMinutes(2)));
        var authClient = new AuthServiceTokenClient(GameRatingConfiguration.restClient(RestClient.builder(), auth.baseUrl(),
                properties.connectTimeout(), properties.readTimeout()), Clock.systemUTC(), properties);
        var reviewClient = new ReviewRatingClient(GameRatingConfiguration.restClient(RestClient.builder(), review.baseUrl(),
                properties.connectTimeout(), properties.readTimeout()), authClient, "review-service", Duration.ZERO, "/internal/v1/games/rating-summaries");
        return new GameRatingSyncService(reviewClient, authClient, repository, properties, Clock.systemUTC());
    }

    static String item(int suffix, String average, long count) {
        return "{\"gameId\":\"00000000-0000-4000-8000-%012d\",\"averageRating\":%s,\"ratingCount\":%d}".formatted(suffix, average, count);
    }

    @Test void tokenThenPagedScanThenRatingFilterAndSortWorkOnTheLocalProjection() {
        game("Alpha", 1);
        game("Bravo", 2);
        game("Charlie", 3);
        game("Delta", 4);
        auth.enqueue(Reply.json(200, TOKEN_BODY));
        review.enqueue(
                Reply.json(200, "{\"items\":[" + item(1, "9.00", 4) + "," + item(2, "3.50", 2) + "],\"nextAfter\":\"00000000-0000-4000-8000-000000000002\"}"),
                Reply.json(200, "{\"items\":[" + item(3, "7.25", 10) + "],\"nextAfter\":null}"));

        // Tarama bitmeden: filtre ve sıralama kapalı, puan durumu "alınamadı".
        assertEquals(RatingStatus.UNAVAILABLE, discovery.list(new DiscoveryQuery(0, 10)).items().getFirst().rating().status());

        assertEquals(Outcome.COMPLETED, service(SECRET).synchronize());

        assertEquals(1, auth.received().size(), "token tek kez alındı ve ikinci sayfada önbellekten kullanıldı");
        assertTrue(auth.received().getFirst().body().contains("\"audience\":\"review-service\""));
        assertEquals(2, review.received().size());
        review.received().forEach(request -> assertEquals("Bearer svc-token", request.authorization()));
        assertEquals("/internal/v1/games/rating-summaries?limit=2", review.received().get(0).pathAndQuery());
        assertEquals("/internal/v1/games/rating-summaries?limit=2&after=00000000-0000-4000-8000-000000000002", review.received().get(1).pathAndQuery());

        var filtered = discovery.list(new DiscoveryQuery(0, 10, null, null, null, null, null, null, null, false,
                new BigDecimal("5"), CatalogSort.RATING, CatalogDirection.DESC));
        assertEquals(List.of("Alpha", "Charlie"), filtered.items().stream().map(item -> item.game().title()).toList());
        assertEquals(RatingStatus.RATED, filtered.items().getFirst().rating().status());
        assertEquals(0, new BigDecimal("9.00").compareTo(filtered.items().getFirst().rating().averageRating()));
        assertEquals(4L, filtered.items().getFirst().rating().ratingCount());

        var all = discovery.list(new DiscoveryQuery(0, 10, null, null, null, null, null, null, null, false, null,
                CatalogSort.RATING, CatalogDirection.ASC));
        assertEquals(List.of("Bravo", "Charlie", "Alpha", "Delta"), all.items().stream().map(item -> item.game().title()).toList());
        assertEquals(RatingStatus.UNRATED, all.items().getLast().rating().status());
        assertEquals(0L, all.items().getLast().rating().ratingCount());
    }

    @Test void reviewRejectionKeepsRatingsUnavailableAndNeverFallsBackToAnotherToken() {
        game("Alpha", 1);
        auth.enqueue(Reply.json(200, TOKEN_BODY));
        review.enqueue(Reply.json(403, "{}"));
        assertEquals(Outcome.FAILED, service(SECRET).synchronize());
        assertEquals(1, review.received().size(), "403'te yeniden deneme yok");
        assertEquals(1, auth.received().size(), "kullanıcı token'ına veya başka yola düşülmedi");
        assertTrue(repository.lastCompletedAt().isEmpty());
        assertEquals(RatingStatus.UNAVAILABLE, discovery.list(new DiscoveryQuery(0, 10)).items().getFirst().rating().status());
    }

    @Test void authOutageNeverReachesReviewAndMissingSecretDoesNothing() {
        game("Alpha", 1);
        auth.enqueue(Reply.json(503, "{}"));
        assertEquals(Outcome.FAILED, service(SECRET).synchronize());
        assertEquals(Outcome.NOT_CONFIGURED, service("").synchronize());
        assertTrue(review.received().isEmpty());
        assertEquals(1, auth.received().size(), "gizli anahtar yoksa Auth'a da istek atılmaz");
    }
}
