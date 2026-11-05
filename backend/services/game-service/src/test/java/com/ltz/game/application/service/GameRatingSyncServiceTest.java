package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.ReviewRatingPort;
import com.ltz.game.application.port.ReviewRatingPort.RatingPage;
import com.ltz.game.application.port.ReviewRatingPort.RatingSummary;
import com.ltz.game.application.port.ServiceTokenPort;
import com.ltz.game.application.service.GameRatingSyncService.Outcome;
import com.ltz.game.domain.exception.ReviewRatingUnavailableException;
import com.ltz.game.infrastructure.config.GameRatingProperties;
import com.ltz.game.infrastructure.persistence.repository.GameRatingProjectionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gerçek PostgreSQL + sahte Review portu: tarama protokolü (devir notu §3.4) ve çok örnekli kiralama. */
@SpringBootTest
class GameRatingSyncServiceTest extends PostgresTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired GameRatingProjectionRepository repository;
    FakeReview reviews;
    FakeTokens tokens;
    GameRatingSyncService service;

    static UUID id(int number) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(number));
    }

    static RatingSummary rating(int number, String average, long count) {
        return new RatingSummary(id(number), new BigDecimal(average), count);
    }

    static final class FakeTokens implements ServiceTokenPort {
        boolean configured = true;
        @Override public boolean isConfigured() { return configured; }
        @Override public String tokenFor(String audience) { return "fake"; }
        @Override public void invalidate(String audience) { }
    }

    static final class FakeReview implements ReviewRatingPort {
        final Queue<Supplier<RatingPage>> pages = new LinkedList<>();
        final List<UUID> cursors = new ArrayList<>();
        int calls;

        FakeReview page(UUID next, RatingSummary... items) {
            pages.add(() -> new RatingPage(List.of(items), next));
            return this;
        }

        FakeReview failure(String code) {
            pages.add(() -> { throw new ReviewRatingUnavailableException(code); });
            return this;
        }

        @Override public RatingPage fetchPage(UUID after, int limit) {
            calls++;
            cursors.add(after);
            return pages.remove().get();
        }
    }

    @BeforeEach void setup() {
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        jdbc.update("""
                update game_rating_sync_state set scan_generation = 0, lease_owner = null, lease_until = null,
                    last_attempt_at = null, last_completed_scan = null, last_completed_at = null, last_error_code = null""");
        reviews = new FakeReview();
        tokens = new FakeTokens();
        var properties = new GameRatingProperties("http://unused", "http://unused", "/t", "game-service", "x", "review-service",
                Duration.ofSeconds(1), Duration.ofSeconds(2), 2,
                new GameRatingProperties.Sync(true, Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofMinutes(2)));
        service = new GameRatingSyncService(reviews, tokens, repository, properties, Clock.systemUTC());
    }

    void games(int... numbers) {
        for (int number : numbers) {
            jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at) values (?,?,?,now(),now())",
                    id(number), "Game " + number, "game " + number);
        }
    }

    Map<String, Object> row(int number) {
        return jdbc.queryForMap("select average_rating, rating_count, last_seen_scan from game_rating_projection where game_id=?", id(number));
    }

    int rows() {
        return jdbc.queryForObject("select count(*) from game_rating_projection", Integer.class);
    }

    Map<String, Object> state() {
        return jdbc.queryForMap("select * from game_rating_sync_state");
    }

    @Test void fullMultiPageScanStoresEveryPageAndMarksProjectionReady() {
        games(1, 2, 3, 4);
        reviews.page(id(2), rating(1, "7.50", 2), rating(2, "10.00", 1)).page(null, rating(3, "1.00", 5), rating(4, "6.25", 8));
        assertTrue(repository.lastCompletedAt().isEmpty());
        assertEquals(Outcome.COMPLETED, service.synchronize());
        assertEquals(java.util.Arrays.asList(null, id(2)), reviews.cursors);
        assertEquals(4, rows());
        assertEquals(0, new BigDecimal("6.25").compareTo((BigDecimal) row(4).get("average_rating")));
        assertEquals(8L, row(4).get("rating_count"));
        assertTrue(repository.lastCompletedAt().isPresent());
        assertNull(state().get("lease_owner"), "kiralama bırakılmalı");
        assertNull(state().get("last_error_code"));
        assertEquals(1L, state().get("last_completed_scan"));
    }

    @Test void gamesUnknownToTheCatalogAreSkippedWithoutFailingTheScan() {
        games(1);
        reviews.page(null, rating(1, "5.00", 1), rating(2, "9.00", 3));
        assertEquals(Outcome.COMPLETED, service.synchronize());
        assertEquals(1, rows());
        assertEquals(0, jdbc.queryForObject("select count(*) from game_rating_projection where game_id=?", Integer.class, id(2)));
    }

    @Test void completedScanUnratesRowsItDidNotSee() {
        games(1, 2, 3);
        reviews.page(id(2), rating(1, "5.00", 1), rating(2, "6.00", 2)).page(null, rating(3, "7.00", 3));
        assertEquals(Outcome.COMPLETED, service.synchronize());
        reviews.page(null, rating(1, "4.00", 4));
        assertEquals(Outcome.COMPLETED, service.synchronize());
        assertEquals(0, new BigDecimal("4.00").compareTo((BigDecimal) row(1).get("average_rating")));
        for (int unseen : new int[]{2, 3}) {
            assertNull(row(unseen).get("average_rating"));
            assertEquals(0L, row(unseen).get("rating_count"));
            assertEquals(2L, row(unseen).get("last_seen_scan"));
        }
    }

    @Test void interruptedScanNeverUnratesAnythingAndKeepsThePreviousCompletionTime() {
        games(1, 2, 3);
        reviews.page(null, rating(1, "5.00", 1), rating(2, "6.00", 2));
        assertEquals(Outcome.COMPLETED, service.synchronize());
        var completedAt = repository.lastCompletedAt().orElseThrow();

        reviews.page(id(1), rating(1, "9.00", 9)).failure(ReviewRatingUnavailableException.REVIEW_UNAVAILABLE);
        assertEquals(Outcome.FAILED, service.synchronize());
        assertEquals(0, new BigDecimal("6.00").compareTo((BigDecimal) row(2).get("average_rating")), "görülmeyen satır sıfırlanmaz");
        assertEquals(2L, row(2).get("rating_count"));
        assertEquals(0, new BigDecimal("9.00").compareTo((BigDecimal) row(1).get("average_rating")), "alınan sayfa korunur");
        assertEquals(completedAt, repository.lastCompletedAt().orElseThrow());
        assertEquals(ReviewRatingUnavailableException.REVIEW_UNAVAILABLE, state().get("last_error_code"));
        assertNull(state().get("lease_owner"));

        reviews.page(null, rating(1, "9.00", 9), rating(2, "6.00", 2));
        assertEquals(Outcome.COMPLETED, service.synchronize());
        assertNull(state().get("last_error_code"));
    }

    @Test void rejectionOnTheFirstEverScanKeepsTheProjectionNotReady() {
        games(1);
        for (String code : new String[]{ReviewRatingUnavailableException.REVIEW_REJECTED, ReviewRatingUnavailableException.TOKEN_UNAVAILABLE,
                ReviewRatingUnavailableException.INVALID_RESPONSE}) {
            reviews.failure(code);
            assertEquals(Outcome.FAILED, service.synchronize());
            assertTrue(repository.lastCompletedAt().isEmpty());
            assertEquals(code, state().get("last_error_code"));
        }
        assertEquals(0, rows());
    }

    @Test void unexpectedErrorsAreRecordedWithAGenericCode() {
        reviews.pages.add(() -> { throw new IllegalStateException("boom with details"); });
        assertEquals(Outcome.FAILED, service.synchronize());
        assertEquals("SYNC_ERROR", state().get("last_error_code"));
        assertFalse(String.valueOf(state().get("last_error_code")).contains("boom"));
    }

    @Test void unconfiguredClientDoesNotTouchReviewOrState() {
        tokens.configured = false;
        assertEquals(Outcome.NOT_CONFIGURED, service.synchronize());
        assertEquals(0, reviews.calls);
        assertEquals(0L, state().get("scan_generation"));
    }

    @Test void onlyOneInstanceScansAtATimeAndExpiredLeaseIsTakenOver() {
        games(1);
        assertTrue(repository.startScan(UUID.randomUUID(), Duration.ofMinutes(2)).isPresent());
        assertEquals(Outcome.ALREADY_RUNNING, service.synchronize());
        assertEquals(0, reviews.calls);

        jdbc.update("update game_rating_sync_state set lease_until = now() - interval '1 second'");
        reviews.page(null, rating(1, "5.00", 1));
        assertEquals(Outcome.COMPLETED, service.synchronize());
        assertEquals(2L, state().get("last_completed_scan"));
    }

    @Test void lostLeaseStopsTheScanWithoutUnratingOrCompleting() {
        games(1, 2);
        reviews.page(null, rating(1, "5.00", 1), rating(2, "6.00", 2));
        assertEquals(Outcome.COMPLETED, service.synchronize());
        var completedAt = repository.lastCompletedAt().orElseThrow();
        UUID other = UUID.randomUUID();

        reviews.pages.add(() -> {
            // Bu tarama yavaşlarken başka bir örnek kiralamayı devraldı.
            jdbc.update("update game_rating_sync_state set lease_until = now() - interval '1 second'");
            repository.startScan(other, Duration.ofMinutes(2));
            return new RatingPage(List.of(rating(1, "8.00", 3)), id(1));
        });
        assertEquals(Outcome.FAILED, service.synchronize());
        assertEquals(2L, row(2).get("rating_count"), "kiralama kaybında sıfırlama yok");
        assertEquals(completedAt, repository.lastCompletedAt().orElseThrow());
        assertEquals(other, state().get("lease_owner"), "kayıp tarama devralanın kiralamasını bırakamaz");
    }

    @Test void staleScanCannotOverwriteNewerRowsOrCompleteAfterBeingReplaced() {
        games(1);
        UUID stale = UUID.randomUUID(), fresh = UUID.randomUUID();
        long first = repository.startScan(stale, Duration.ofMinutes(2)).orElseThrow();
        jdbc.update("update game_rating_sync_state set lease_until = now() - interval '1 second'");
        long second = repository.startScan(fresh, Duration.ofMinutes(2)).orElseThrow();
        assertTrue(second > first);

        repository.upsertPage(second, List.of(rating(1, "9.00", 4)), java.time.Instant.now());
        repository.upsertPage(first, List.of(rating(1, "1.00", 1)), java.time.Instant.now());
        assertEquals(0, new BigDecimal("9.00").compareTo((BigDecimal) row(1).get("average_rating")));
        assertFalse(repository.extendLease(stale, first, Duration.ofMinutes(2)));
        assertFalse(repository.complete(stale, first));
        assertTrue(repository.lastCompletedAt().isEmpty());
        assertTrue(repository.complete(fresh, second));
        assertNotNull(repository.lastCompletedAt().orElse(null));
    }

    @Test void databaseRejectsOutOfRangeAndInconsistentProjectionRows() {
        games(1);
        for (String values : new String[]{"0.99, 1", "10.01, 1", "5.00, 0", "null, 3"}) {
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                    "insert into game_rating_projection(game_id,average_rating,rating_count,last_seen_scan,synced_at) values (?," + values + ",1,now())", id(1)));
        }
    }
}
