package com.ltz.review.infrastructure.events;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.application.service.AccountErasureService;
import com.ltz.review.application.service.ReviewService;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.UserSummaryOutbox;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REV-10: ReviewSummaryChanged outbox ve teslim. Auth ve User yerel sahte HTTP sunucularıdır; canlı servis kanıtı DEĞİLDİR. */
@SpringBootTest
class UserSummaryRelayTest {
    record Received(String authorization, String body) {}

    @Autowired JdbcClient jdbc;
    @Autowired ReviewService reviews;
    @Autowired UserSummaryOutbox outbox;
    @Autowired AccountErasureService erasure;
    @Autowired ApplicationContext context;
    @MockitoBean GameCatalogPort games;

    HttpServer server;
    final List<Received> userCalls = new CopyOnWriteArrayList<>();
    final AtomicInteger tokenCalls = new AtomicInteger();
    final AtomicInteger userStatus = new AtomicInteger(200);
    final AtomicInteger tokenStatus = new AtomicInteger(200);
    final AtomicReference<Runnable> duringUserCall = new AtomicReference<>();
    UserSummaryRelay relay;
    UUID user;

    @BeforeEach void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/service-tokens", exchange -> {
            tokenCalls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            respond(exchange, tokenStatus.get(), "{\"accessToken\":\"token-" + tokenCalls.get() + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"events:ingest\"}");
        });
        server.createContext("/internal/events", exchange -> {
            userCalls.add(new Received(exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            var hook = duringUserCall.get();
            if (hook != null) hook.run();
            respond(exchange, userStatus.get(), "{\"eventId\":\"x\",\"outcome\":\"PROCESSED\"}");
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var clock = Clock.systemUTC();
        var tokens = new ServiceTokenClient(RestClient.builder().baseUrl(base).build(), clock, "review-service", "test-secret");
        relay = new UserSummaryRelay(outbox, tokens, RestClient.builder().baseUrl(base).build(), clock, 50);
        user = UUID.randomUUID();
        // Ortak test veritabanında diğer testlerin bekleyen satırları bu turlara karışmasın.
        jdbc.sql("DELETE FROM review_user_summary_outbox").update();
    }

    @AfterEach void stop() { server.stop(0); }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    UUID write(UUID owner) { return reviews.create(owner, UUID.randomUUID(), "Deneyim").id(); }

    java.util.Map<String, Object> row(UUID owner) {
        return jdbc.sql("SELECT event_id, occurred_at, attempts, next_attempt_at, delivered_at, last_error FROM review_user_summary_outbox WHERE user_id = ?")
                .param(owner).query().singleRow();
    }

    void makeDueNow(UUID owner) {
        jdbc.sql("UPDATE review_user_summary_outbox SET next_attempt_at = clock_timestamp() - interval '1 second' WHERE user_id = ?").param(owner).update();
    }

    @Test void creatingAndDeletingAReviewTouchesTheOutboxWithStrictlyIncreasingTimes() {
        UUID id = write(user);
        var first = row(user);
        assertNull(first.get("delivered_at"));
        reviews.delete(user, id, 0);
        var second = row(user);
        assertNotEquals(first.get("event_id"), second.get("event_id"));
        assertTrue(((java.sql.Timestamp) second.get("occurred_at")).after((java.sql.Timestamp) first.get("occurred_at")));
        assertEquals(0, second.get("attempts"));
    }

    @Test void aRejectedWriteLeavesNoOutboxRow() {
        UUID game = UUID.randomUUID();
        reviews.create(user, game, "İlk");
        jdbc.sql("DELETE FROM review_user_summary_outbox WHERE user_id = ?").param(user).update();
        // 409: aynı oyuna ikinci aktif inceleme; reddedilen yazma outbox'a satır bırakmaz.
        var rejected = assertThrows(ReviewException.class, () -> reviews.create(user, game, "İkinci"));
        assertEquals(409, rejected.status());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_user_summary_outbox WHERE user_id = ?").param(user).query(Long.class).single());
    }

    @Test void deliversTheCurrentVisibleCountWithAServiceTokenAndMarksTheRowDelivered() {
        write(user);
        assertEquals(1, relay.publishDue());
        assertEquals(1, userCalls.size());
        var call = userCalls.getFirst();
        assertEquals("Bearer token-1", call.authorization());
        for (String part : List.of("\"eventType\":\"ReviewSummaryChanged\"", "\"eventVersion\":1", "\"producer\":\"review-service\"",
                "\"aggregateId\":\"" + user + "\"", "\"userId\":\"" + user + "\"", "\"reviewCount\":1")) assertTrue(call.body().contains(part), part);
        assertNotNull(row(user).get("delivered_at"));
        assertEquals(0, relay.publishDue());
        assertEquals(1, userCalls.size());
    }

    @Test void aDeleteProducesANewEventWithTheLowerCountAndTheTokenIsCached() {
        UUID id = write(user);
        relay.publishDue();
        reviews.delete(user, id, 0);
        assertEquals(1, relay.publishDue());
        assertEquals(2, userCalls.size());
        assertTrue(userCalls.get(1).body().contains("\"reviewCount\":0"));
        assertEquals(1, tokenCalls.get());
        assertNotEquals(extract(userCalls.get(0).body(), "eventId"), extract(userCalls.get(1).body(), "eventId"));
    }

    static String extract(String json, String key) {
        int start = json.indexOf("\"" + key + "\":\"") + key.length() + 4;
        return json.substring(start, json.indexOf('"', start));
    }

    @Test void aServerErrorKeepsTheSameEventPendingWithBackoffAndRetriesItWithTheSameId() {
        write(user);
        userStatus.set(503);
        assertEquals(0, relay.publishDue());
        var failed = row(user);
        assertNull(failed.get("delivered_at"));
        assertEquals(1, failed.get("attempts"));
        assertEquals("USER_503", failed.get("last_error"));
        assertTrue(((java.sql.Timestamp) failed.get("next_attempt_at")).after(new java.sql.Timestamp(System.currentTimeMillis())));
        // Geri çekilme süresince yeniden gönderilmez.
        assertEquals(0, relay.publishDue());
        assertEquals(1, userCalls.size());
        userStatus.set(200);
        makeDueNow(user);
        assertEquals(1, relay.publishDue());
        assertEquals(extract(userCalls.get(0).body(), "eventId"), extract(userCalls.get(1).body(), "eventId"));
    }

    @Test void anAuthorizationFailureIsRecordedAndTheTokenIsRefreshedOnTheNextAttempt() {
        write(user);
        userStatus.set(401);
        assertEquals(0, relay.publishDue());
        assertEquals("USER_REJECTED_401", row(user).get("last_error"));
        userStatus.set(200);
        makeDueNow(user);
        assertEquals(1, relay.publishDue());
        assertEquals(2, tokenCalls.get());
        assertEquals("Bearer token-2", userCalls.get(1).authorization());
    }

    @Test void whenAuthCannotIssueATokenUserIsNeverCalled() {
        write(user);
        tokenStatus.set(403);
        assertEquals(0, relay.publishDue());
        assertEquals("TOKEN_UNAVAILABLE", row(user).get("last_error"));
        assertTrue(userCalls.isEmpty());
    }

    @Test void aChangeDuringDeliveryKeepsTheRowPendingForANewerEvent() {
        write(user);
        var before = row(user).get("event_id");
        duringUserCall.set(() -> write(user));
        assertEquals(1, relay.publishDue());
        duringUserCall.set(null);
        var after = row(user);
        assertNull(after.get("delivered_at"));
        assertNotEquals(before, after.get("event_id"));
        assertEquals(1, relay.publishDue());
        assertTrue(userCalls.get(1).body().contains("\"reviewCount\":2"));
        assertNotNull(row(user).get("delivered_at"));
    }

    @Test void backfillQueuesEveryUserWithReviewsOnceAndLeavesExistingRowsUntouched() {
        jdbc.sql("DELETE FROM reviews").update();
        UUID early = UUID.randomUUID();
        UUID deletedOnly = UUID.randomUUID();
        seedReview(early, "VISIBLE");
        seedReview(early, "VISIBLE");
        seedReview(user, "VISIBLE");
        seedReview(deletedOnly, "DELETED");
        // outbox'ı olan kullanıcı korunur.
        jdbc.sql("INSERT INTO review_user_summary_outbox (user_id, event_id, occurred_at, next_attempt_at) VALUES (?, gen_random_uuid(), clock_timestamp(), clock_timestamp())").param(user).update();
        var existing = row(user).get("event_id");
        assertEquals(2, outbox.backfillMissing(java.time.Instant.now()));
        assertEquals(existing, row(user).get("event_id"));
        assertNotNull(row(early));
        assertNotNull(row(deletedOnly));
        assertEquals(0, outbox.backfillMissing(java.time.Instant.now()));
        // Teslim, gönderim anındaki görünür sayıyı taşır.
        assertEquals(3, relay.publishDue());
        var counts = userCalls.stream().filter(call -> call.body().contains(early.toString())).findFirst().orElseThrow();
        assertTrue(counts.body().contains("\"reviewCount\":2"));
        var none = userCalls.stream().filter(call -> call.body().contains(deletedOnly.toString())).findFirst().orElseThrow();
        assertTrue(none.body().contains("\"reviewCount\":0"));
    }

    void seedReview(UUID owner, String status) {
        boolean visible = !"DELETED".equals(status);
        jdbc.sql("INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at,deleted_at) VALUES (?,?,?,?,?,clock_timestamp(),clock_timestamp(),CASE WHEN ? THEN NULL ELSE clock_timestamp() END)")
                .params(UUID.randomUUID(), owner, UUID.randomUUID(), visible ? "Deneyim" : null, status, visible).update();
    }

    @Test void usersAreIndependentAndOrderedByTime() {
        UUID other = UUID.randomUUID();
        write(user);
        write(other);
        write(other);
        assertEquals(2, relay.publishDue());
        List<String> counts = new ArrayList<>();
        for (var call : userCalls) counts.add(call.body().contains(user.toString()) ? "u" : "o");
        assertEquals(List.of("u", "o"), counts);
        assertTrue(userCalls.get(1).body().contains("\"reviewCount\":2"));
    }

    @Test void erasingTheAccountRemovesItsPendingEventAndNothingIsSentForIt() {
        write(user);
        erasure.erase(user);
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_user_summary_outbox WHERE user_id = ?").param(user).query(Long.class).single());
        assertEquals(0, relay.publishDue());
        assertTrue(userCalls.isEmpty());
    }

    @Test void theRelayIsNotCreatedWithoutAnAuthClientSecret() {
        assertFalse(context.getBeanNamesForType(UserSummaryRelay.class).length > 0);
    }
}
