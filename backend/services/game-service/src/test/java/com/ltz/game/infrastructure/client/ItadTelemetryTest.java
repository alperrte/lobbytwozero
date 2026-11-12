package com.ltz.game.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/** ITAD gözlemi: 429 tek satırda pencere doluluğuyla loglanır, özet dakikada bir çıkar, hassas değer ve ham başlık güvenli kısaltılır. */
class ItadTelemetryTest {
    static class TickClock extends Clock {
        Instant now = Instant.parse("2026-11-12T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final Logger logger = (Logger) LoggerFactory.getLogger(ItadTelemetry.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final ItadTelemetry.Call prices = new ItadTelemetry.Call("prices", "DE-EUR", false);

    private Level previousLevel;

    /** Test logback ayarı kökü WARN tutar; INFO satırlarını yakalamak için seviye tek başına koşulduğunda da açılır. */
    @BeforeEach void attach() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach void detach() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    private List<String> lines() { return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList(); }

    @Test void aRateLimitIsLoggedOnceWithTheWindowFillTheRawHeaderTheBlockAndTheStreak() {
        TickClock clock = new TickClock();
        var telemetry = new ItadTelemetry(clock);
        var snapshot = new ItadRequestLimiter.Snapshot(412, 88, 14, 850, 150, 850);
        telemetry.start();
        telemetry.rateLimited(prices, snapshot, "120", Duration.ofSeconds(120), 297);
        clock.now = clock.now.plusSeconds(90);
        telemetry.rateLimited(new ItadTelemetry.Call("lookup", "DE-EUR", true), snapshot, null, Duration.ofSeconds(600), 297);
        var first = lines().getFirst();
        assertTrue(first.contains("olay=429") && first.contains("yol=prices") && first.contains("pazar=DE-EUR") && first.contains("oncelik=BACKGROUND"), first);
        assertTrue(first.contains("pencere_ici=412") && first.contains("son60sn=88") && first.contains("son10sn=14") && first.contains("eszamanli=1"), first);
        assertTrue(first.contains("limit=850") && first.contains("rezerv=150") && first.contains("retry_after_ham=\"120\"") && first.contains("engel_sn=120"), first);
        assertTrue(first.contains("ardisik=1") && first.contains("onceki_429_sn=-1"), first);
        var second = lines().get(1);
        assertTrue(second.contains("yol=lookup") && second.contains("oncelik=INTERACTIVE") && second.contains("retry_after_ham=\"yok\"") && second.contains("engel_sn=600"), second);
        assertTrue(second.contains("ardisik=2") && second.contains("onceki_429_sn=90"), second);
    }

    @Test void aSuccessfulResponseEndsTheStreak() {
        TickClock clock = new TickClock();
        var telemetry = new ItadTelemetry(clock);
        var snapshot = new ItadRequestLimiter.Snapshot(1, 1, 1, 850, 150, 850);
        telemetry.rateLimited(prices, snapshot, "5", Duration.ofSeconds(5), 297);
        telemetry.start();
        telemetry.finish(prices, 200, 100);
        telemetry.rateLimited(prices, snapshot, "5", Duration.ofSeconds(5), 297);
        assertTrue(lines().getLast().contains("ardisik=1"), lines().getLast());
    }

    @Test void aSummaryIsLoggedAtMostOncePerMinuteWithKindCountsErrorsAndAverageLatency() {
        TickClock clock = new TickClock();
        var telemetry = new ItadTelemetry(clock);
        telemetry.start(); telemetry.finish(prices, 200, 100);
        telemetry.start(); telemetry.finish(new ItadTelemetry.Call("lookup", "DE-EUR", false), 200, 300);
        telemetry.start(); telemetry.finish(prices, 429, 200);
        telemetry.start(); telemetry.finish(prices, 0, 0);
        assertTrue(lines().isEmpty(), "dakika dolmadan özet yok");
        clock.now = clock.now.plusSeconds(61);
        telemetry.start(); telemetry.finish(prices, 200, 400);
        var summary = lines().getLast();
        assertTrue(summary.startsWith("ITAD ozet:"), summary);
        assertTrue(summary.contains("sure_sn=61") && summary.contains("prices=4") && summary.contains("lookup=1"), summary);
        assertTrue(summary.contains("e429=1") && summary.contains("diger_hata=1") && summary.contains("ort_ms=200"), summary);
        int before = lines().size();
        telemetry.start(); telemetry.finish(prices, 200, 10);
        assertEquals(before, lines().size(), "aynı dakikada ikinci özet yok");
    }

    @Test void rateLimitHeadersAreOnlyObservedAndLoggedAtMostOncePerMinuteInASafeForm() {
        TickClock clock = new TickClock();
        var telemetry = new ItadTelemetry(clock);
        var headers = new HttpHeaders();
        headers.add("X-RateLimit-Remaining", "42");
        headers.add("RateLimit-Reset", "30; evil=\"<x>\"");
        headers.add("Content-Type", "application/json");
        telemetry.observeHeaders(headers);
        assertEquals(1, lines().size());
        var line = lines().getFirst();
        assertTrue(line.startsWith("ITAD kotasi: olay=baslik"), line);
        assertTrue(line.contains("x-ratelimit-remaining=\"42\"") && line.contains("ratelimit-reset=\""), line);
        assertFalse(line.contains("<") || line.contains(">") || line.contains("application/json"), line);
        telemetry.observeHeaders(headers);
        assertEquals(1, lines().size(), "dakika dolmadan tekrar loglanmaz");
        clock.now = clock.now.plusSeconds(61);
        telemetry.observeHeaders(headers);
        assertEquals(2, lines().size());
        telemetry.observeHeaders(new HttpHeaders());
        assertEquals(2, lines().size(), "başlık yoksa log yok");
    }

    @Test void rawHeaderValuesAreSanitizedAndTruncated() {
        assertEquals("yok", ItadTelemetry.sanitize(null));
        assertEquals("120", ItadTelemetry.sanitize("120"));
        assertEquals("Thu 5 Nov 2026 140000 GMT", ItadTelemetry.sanitize("Thu, 5 Nov 2026 14:00:00 GMT").replace(",", "").replace(":", ""));
        assertEquals("abc", ItadTelemetry.sanitize("a\"b\nc<>"));
        assertEquals(40, ItadTelemetry.sanitize("x".repeat(500)).length());
    }
}
