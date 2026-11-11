package com.ltz.messaging.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.infrastructure.config.GifProperties;

/**
 * Kullanıcı başına dakikalık sabit pencere sınırı (GIF arama, tepki). Sayaç DB'dedir: birden çok örnek aynı sınırı
 * paylaşır (her örnek kendi sınırını uygulamaz).
 */
class GifSearchRateLimiterTest extends IntegrationTestSupport {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T10:00:05Z"));

    @Test
    void dakikadaSinirKadarAramaIzinliSonrakiReddedilir() {
        GifSearchRateLimiter limiter = limiter();
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
    void ikiOrnekAyniSiniriPaylasir() {
        GifSearchRateLimiter first = limiter();
        GifSearchRateLimiter second = limiter();
        UUID user = UUID.randomUUID();

        assertThat(first.tryAcquire(user)).isTrue();
        assertThat(second.tryAcquire(user)).isTrue();
        assertThat(first.tryAcquire(user)).isTrue();
        assertThat(second.tryAcquire(user)).isFalse();
        assertThat(first.tryAcquire(user)).isFalse();
    }

    @Test
    void eskiPencerelerTemizlenirGuncelPencereKalir() {
        GifSearchRateLimiter limiter = limiter();
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

        assertThat(jdbc.queryForObject("select count(*) from rate_limit_windows where user_id = ?", Integer.class,
                old)).isZero();
        assertThat(limiter.tryAcquire(old)).isTrue();
        assertThat(limiter.tryAcquire(current)).isFalse();
    }

    private GifSearchRateLimiter limiter() {
        return new GifSearchRateLimiter(new GifProperties("https://api.giphy.test", "", "g", 24, 50, 499, 50, 3),
                jdbc, clock);
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
