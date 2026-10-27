package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ltz.auth.domain.exception.PasswordHashingCapacityExceededException;

class BoundedPasswordEncoderTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(50);

    @Test
    void kapasiteDoluysaIstekBeklemeSuresiSonundaReddedilir() throws Exception {
        CountDownLatch hashing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(new BlockingEncoder(hashing, release), 1,
                SHORT_TIMEOUT);
        Thread holder = Thread.ofVirtual().start(() -> encoder.encode("first"));
        assertThat(hashing.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> encoder.encode("second"))
                .isInstanceOf(PasswordHashingCapacityExceededException.class);

        release.countDown();
        holder.join();
        assertThat(encoder.encode("third")).isEqualTo("encoded");
    }

    @Test
    void kesintiyeUgrayanBeklemeReddedilir_kesintiBayragiKorunur() {
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(new BlockingEncoder(null, null), 1, SHORT_TIMEOUT);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> encoder.encode("password"))
                    .isInstanceOf(PasswordHashingCapacityExceededException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void eslesmeVeYukseltmeKarariAsilEncoderaDevredilir() {
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(new BlockingEncoder(null, null), 1, SHORT_TIMEOUT);

        assertThat(encoder.matches("raw", "encoded")).isTrue();
        assertThat(encoder.matches("raw", "other")).isFalse();
        assertThat(encoder.upgradeEncoding("legacy")).isTrue();
    }

    private record BlockingEncoder(CountDownLatch hashing, CountDownLatch release) implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            if (hashing != null && "first".contentEquals(rawPassword)) {
                hashing.countDown();
                try {
                    release.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            }
            return "encoded";
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return "encoded".equals(encodedPassword);
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return "legacy".equals(encodedPassword);
        }
    }
}
