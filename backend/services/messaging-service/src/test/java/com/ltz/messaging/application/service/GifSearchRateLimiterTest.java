package com.ltz.messaging.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.infrastructure.config.GifProperties;

/** Kullanıcı başına dakikalık GIF arama sınırı: sınırda durur, kullanıcılar birbirini etkilemez, yeni dakikada açılır. */
class GifSearchRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T10:00:05Z"));
    private final GifSearchRateLimiter limiter = new GifSearchRateLimiter(
            new GifProperties("https://api.giphy.test", "", "g", 24, 50, 499, 50, 3), clock);

    @Test
    void dakikadaSinirKadarAramaIzinliSonrakiReddedilir() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertThat(IntStream.range(0, 3).mapToObj(index -> limiter.tryAcquire(user))).containsOnly(true);
        assertThat(limiter.tryAcquire(user)).isFalse();
        assertThat(limiter.tryAcquire(user)).isFalse();
        assertThat(limiter.tryAcquire(other)).isTrue();

        clock.set(Instant.parse("2026-11-09T10:01:00Z"));
        assertThat(limiter.tryAcquire(user)).isTrue();
    }

    @Test
    void eskiPencerelerTemizlenirGuncelPencereKalir() {
        UUID old = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        limiter.tryAcquire(old);
        limiter.tryAcquire(old);
        limiter.tryAcquire(old);
        clock.set(Instant.parse("2026-11-09T10:01:30Z"));
        limiter.tryAcquire(current);
        limiter.tryAcquire(current);
        limiter.tryAcquire(current);

        limiter.evictExpired();

        assertThat(limiter.tryAcquire(old)).isTrue();
        assertThat(limiter.tryAcquire(current)).isFalse();
    }

    /** Testte zamanı ileri almak için. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
