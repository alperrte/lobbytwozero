package com.ltz.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.user.domain.exception.RateLimitedException;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.infrastructure.config.SearchLimitProperties;

class SearchRateLimiterTest {

    private static final Instant WINDOW_START = Instant.parse("2026-11-18T12:00:00Z");

    /** Elle ilerletilen saat. */
    private static final class TestClock extends Clock {

        private Instant now;

        TestClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static SearchRateLimiter limiter(int perMinute, Clock clock) {
        return new SearchRateLimiter(new SearchLimitProperties(perMinute), clock);
    }

    @Test
    void sinirinAltindaIzinVerirAsinca429SaniyeleFirlatir() {
        TestClock clock = new TestClock(WINDOW_START.plusSeconds(20));
        SearchRateLimiter limiter = limiter(3, clock);
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            limiter.acquire(user);
        }

        assertThatThrownBy(() -> limiter.acquire(user))
                .isInstanceOfSatisfying(RateLimitedException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(UserErrorCode.SEARCH_RATE_LIMITED);
                    assertThat(exception.retryAfterSeconds()).isEqualTo(40);
                });
    }

    @Test
    void sayacKullaniciBasinadir() {
        SearchRateLimiter limiter = limiter(1, new TestClock(WINDOW_START));
        UUID first = UUID.randomUUID();

        limiter.acquire(first);

        assertThatThrownBy(() -> limiter.acquire(first)).isInstanceOf(RateLimitedException.class);
        assertThatCode(() -> limiter.acquire(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void yeniDakikadaSayacSifirlanir() {
        TestClock clock = new TestClock(WINDOW_START.plusSeconds(59));
        SearchRateLimiter limiter = limiter(1, clock);
        UUID user = UUID.randomUUID();

        limiter.acquire(user);
        assertThatThrownBy(() -> limiter.acquire(user))
                .isInstanceOfSatisfying(RateLimitedException.class,
                        exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(1));

        clock.advanceSeconds(1);

        assertThatCode(() -> limiter.acquire(user)).doesNotThrowAnyException();
    }
}
