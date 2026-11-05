package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.ReviewRatingPort.RatingPage;
import com.ltz.game.application.port.ServiceTokenPort;
import com.ltz.game.domain.exception.ReviewRatingUnavailableException;
import com.ltz.game.domain.exception.ServiceTokenUnavailableException;
import com.ltz.game.infrastructure.client.FakeServer.Reply;
import com.ltz.game.infrastructure.config.GameRatingConfiguration;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sahte Review sunucusu: 401/403'te yeniden deneme yok, 5xx/zaman aşımında en çok bir deneme, yanıt doğrulaması. */
class ReviewRatingClientTest {
    private static final String TOKEN = "fake-service-token";
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-4000-8000-000000000003");
    // Java'nın işaretli UUID.compareTo'su ile kanonik metin sırası burada ayrışır: 'f…' (negatif) ile '0…' (pozitif).
    private static final UUID HIGH = UUID.fromString("f0000000-0000-4000-8000-000000000001");

    private static final class FakeTokens implements ServiceTokenPort {
        final List<String> invalidated = new ArrayList<>();
        int issued;
        boolean unavailable;

        @Override public boolean isConfigured() { return true; }
        @Override public String tokenFor(String audience) {
            if (unavailable) throw new ServiceTokenUnavailableException("no token");
            issued++;
            return TOKEN;
        }
        @Override public void invalidate(String audience) { invalidated.add(audience); }
    }

    private static ReviewRatingClient client(FakeServer review, FakeTokens tokens, Duration read) {
        RestClient http = GameRatingConfiguration.restClient(RestClient.builder(), review.baseUrl(), Duration.ofSeconds(1), read);
        return new ReviewRatingClient(http, tokens, "review-service", Duration.ZERO);
    }

    private static ReviewRatingClient client(FakeServer review, FakeTokens tokens) {
        return client(review, tokens, Duration.ofSeconds(2));
    }

    private static String item(UUID id, String average, long count) {
        return "{\"gameId\":\"" + id + "\",\"averageRating\":" + average + ",\"ratingCount\":" + count + "}";
    }

    private static String page(String nextAfter, String... items) {
        return "{\"items\":[" + String.join(",", items) + "],\"nextAfter\":" + (nextAfter == null ? "null" : "\"" + nextAfter + "\"") + ",\"extra\":true}";
    }

    private static ReviewRatingUnavailableException failure(ReviewRatingClient client, UUID after, int limit) {
        return assertThrows(ReviewRatingUnavailableException.class, () -> client.fetchPage(after, limit));
    }

    @Test void fetchesAPageWithBearerTokenAndExclusiveCursor() throws IOException {
        try (FakeServer review = new FakeServer()) {
            review.enqueue(Reply.json(200, page(B.toString(), item(A, "7.50", 2), item(B, "10", 1))));
            var tokens = new FakeTokens();
            RatingPage result = client(review, tokens).fetchPage(UUID.fromString("00000000-0000-4000-8000-000000000000"), 2);
            assertEquals(List.of(A, B), result.items().stream().map(s -> s.gameId()).toList());
            assertEquals(0, new BigDecimal("7.5").compareTo(result.items().getFirst().averageRating()));
            assertEquals(2, result.items().getFirst().ratingCount());
            assertEquals(B, result.nextAfter());
            var request = review.received().getFirst();
            assertEquals("GET", request.method());
            assertEquals("Bearer " + TOKEN, request.authorization());
            assertEquals("/internal/v1/games/rating-summaries?limit=2&after=00000000-0000-4000-8000-000000000000", request.pathAndQuery());
        }
    }

    @Test void firstPageSendsNoCursorAndLastPageHasNoNextCursor() throws IOException {
        try (FakeServer review = new FakeServer()) {
            review.enqueue(Reply.json(200, page(null, item(A, "5.00", 3))));
            RatingPage result = client(review, new FakeTokens()).fetchPage(null, 500);
            assertNull(result.nextAfter());
            assertEquals("/internal/v1/games/rating-summaries?limit=500", review.received().getFirst().pathAndQuery());
        }
    }

    @Test void emptyDatasetIsAValidSinglePage() throws IOException {
        try (FakeServer review = new FakeServer()) {
            review.enqueue(Reply.json(200, page(null)));
            RatingPage result = client(review, new FakeTokens()).fetchPage(null, 200);
            assertTrue(result.items().isEmpty());
            assertNull(result.nextAfter());
        }
    }

    @Test void unauthorizedAndForbiddenAreNeverRetriedAndDiscardTheToken() throws IOException {
        for (int status : new int[]{401, 403}) {
            try (FakeServer review = new FakeServer()) {
                review.enqueue(Reply.json(status, "{\"detail\":\"body must not leak\"}"));
                var tokens = new FakeTokens();
                var error = failure(client(review, tokens), null, 10);
                assertEquals(ReviewRatingUnavailableException.REVIEW_REJECTED, error.code());
                assertEquals(1, review.received().size(), "yeniden deneme yok: " + status);
                assertEquals(List.of("review-service"), tokens.invalidated);
                assertFalse(error.getMessage().contains("leak"));
            }
        }
    }

    @Test void serverErrorIsRetriedExactlyOnceThenSucceeds() throws IOException {
        try (FakeServer review = new FakeServer()) {
            review.enqueue(Reply.json(503, "{}"), Reply.json(200, page(null, item(A, "6.00", 1))));
            RatingPage result = client(review, new FakeTokens()).fetchPage(null, 10);
            assertEquals(1, result.items().size());
            assertEquals(2, review.received().size());
        }
    }

    @Test void persistentServerErrorStopsAfterTheSingleRetry() throws IOException {
        try (FakeServer review = new FakeServer()) {
            review.fallback(Reply.json(500, "{}"));
            var error = failure(client(review, new FakeTokens()), null, 10);
            assertEquals(ReviewRatingUnavailableException.REVIEW_UNAVAILABLE, error.code());
            assertEquals(2, review.received().size());
        }
    }

    @Test void timeoutIsBoundedAndRetriedOnce() throws IOException {
        try (FakeServer review = new FakeServer()) {
            review.fallback(Reply.slow(800));
            long started = System.nanoTime();
            var error = failure(client(review, new FakeTokens(), Duration.ofMillis(150)), null, 10);
            assertEquals(ReviewRatingUnavailableException.REVIEW_UNAVAILABLE, error.code());
            assertEquals(2, review.received().size());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 3000, "zaman aşımı sınırlı olmalı");
            assertTrue(review.abandoned() <= 2);
        }
    }

    @Test void unreachableReviewIsReportedAsUnavailable() throws IOException {
        try (FakeServer review = new FakeServer()) {
            var client = client(review, new FakeTokens(), Duration.ofMillis(300));
            review.close(); // sunucu kapandı; try-with-resources ikinci kapatmayı zararsız yapar
            assertEquals(ReviewRatingUnavailableException.REVIEW_UNAVAILABLE, failure(client, null, 10).code());
        }
    }

    @Test void otherClientErrorsAndRedirectsAreInvalidResponsesWithoutRetry() throws IOException {
        try (FakeServer review = new FakeServer()) {
            review.enqueue(Reply.json(400, "{}"), Reply.redirect("http://127.0.0.1:9/elsewhere"));
            var client = client(review, new FakeTokens());
            assertEquals(ReviewRatingUnavailableException.INVALID_RESPONSE, failure(client, null, 10).code());
            assertEquals(ReviewRatingUnavailableException.INVALID_RESPONSE, failure(client, null, 10).code());
            assertEquals(2, review.received().size(), "yönlendirme izlenmedi, yeniden deneme yok");
        }
    }

    @Test void unavailableTokenNeverReachesReview() throws IOException {
        try (FakeServer review = new FakeServer()) {
            var tokens = new FakeTokens();
            tokens.unavailable = true;
            assertEquals(ReviewRatingUnavailableException.TOKEN_UNAVAILABLE, failure(client(review, tokens), null, 10).code());
            assertTrue(review.received().isEmpty());
        }
    }

    @Test void malformedOrContradictoryResponsesAreRejected() throws IOException {
        List<String> bodies = List.of(
                "not json",
                "{}",
                "{\"items\":null,\"nextAfter\":null}",
                page(null, item(B, "5.00", 1), item(A, "5.00", 1)),                 // azalan sıra
                page(null, item(A, "5.00", 1), item(A, "5.00", 1)),                 // tekrar
                page(null, item(A, "0.99", 1)),                                      // ölçek altı
                page(null, item(A, "10.01", 1)),                                     // ölçek üstü
                page(null, item(A, "5.555", 1)),                                     // üç ondalık
                page(null, item(A, "5.00", 0)),                                      // sayı 0
                page(null, "{\"gameId\":\"" + A + "\",\"averageRating\":null,\"ratingCount\":1}"),
                page(null, "{\"gameId\":null,\"averageRating\":5,\"ratingCount\":1}"),
                page(A.toString(), item(A, "5.00", 1), item(B, "5.00", 1)),         // imleç son oyun değil
                page(B.toString(), item(A, "5.00", 1)),                              // tam sayfa değil ama imleç var
                page(null, item(A, "5.00", 1), item(B, "5.00", 1), item(C, "5.00", 1)), // limit aşımı (limit=2)
                page(null, item(HIGH, "5.00", 1), item(A, "5.00", 1)));              // kanonik metin sırasına aykırı
        for (String body : bodies) {
            try (FakeServer review = new FakeServer()) {
                review.enqueue(Reply.json(200, body));
                var error = failure(client(review, new FakeTokens()), null, 2);
                assertEquals(ReviewRatingUnavailableException.INVALID_RESPONSE, error.code(), body);
            }
        }
    }

    @Test void responseMustAdvancePastTheRequestedCursor() throws IOException {
        try (FakeServer review = new FakeServer()) {
            review.enqueue(Reply.json(200, page(null, item(A, "5.00", 1))));
            assertEquals(ReviewRatingUnavailableException.INVALID_RESPONSE, failure(client(review, new FakeTokens()), B, 10).code());
        }
    }

    @Test void canonicalTextOrderNotSignedUuidOrderDecidesAscending() throws IOException {
        try (FakeServer review = new FakeServer()) {
            // '0…' < 'f…' metin sırasında; Java UUID.compareTo bunu tersine çevirirdi.
            review.enqueue(Reply.json(200, page(null, item(A, "5.00", 1), item(HIGH, "5.00", 1))));
            RatingPage result = client(review, new FakeTokens()).fetchPage(null, 10);
            assertEquals(List.of(A, HIGH), result.items().stream().map(s -> s.gameId()).toList());
        }
    }

    @Test void pageSizeOutsideReviewContractIsRejectedLocally() throws IOException {
        try (FakeServer review = new FakeServer()) {
            var client = client(review, new FakeTokens());
            for (int limit : new int[]{0, 501, -1}) {
                assertThrows(IllegalArgumentException.class, () -> client.fetchPage(null, limit));
            }
            assertTrue(review.received().isEmpty());
        }
    }
}
