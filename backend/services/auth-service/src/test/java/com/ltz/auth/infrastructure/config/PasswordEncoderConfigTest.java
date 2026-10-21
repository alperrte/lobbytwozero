package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final int TIMING_SAMPLES = 5;
    /** Argon2id m=19 MiB, t=2 tipik donanımda onlarca ms sürer; üst sınır yalnız aşırı yavaşlamayı yakalar. */
    private static final Duration MAX_MEDIAN_HASH_TIME = Duration.ofSeconds(1);

    private final PasswordEncoder encoder = new PasswordEncoderConfig()
            .passwordEncoder(new PasswordHashingProperties(1, Duration.ofSeconds(5)));

    @Test
    void parolaOwaspParametreleriyleArgon2idOlarakHashlenir() {
        String encoded = encoder.encode(PASSWORD);

        assertThat(encoded).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, encoded)).isTrue();
        assertThat(encoder.matches(PASSWORD + "x", encoded)).isFalse();
        assertThat(encoder.upgradeEncoding(encoded)).isFalse();
    }

    @Test
    void ayniParolaHerSeferindeFarkliTuzlaHashlenir() {
        assertThat(encoder.encode(PASSWORD)).isNotEqualTo(encoder.encode(PASSWORD));
    }

    @Test
    void parolaIcerigiDonusturulmez() {
        String encoded = encoder.encode(" " + PASSWORD + " ");

        assertThat(encoder.matches(PASSWORD, encoded)).isFalse();
        assertThat(encoder.matches(" " + PASSWORD + " ", encoded)).isTrue();
    }

    @Test
    void hashMaliyetiKabulEdilebilirSuredeKalir() {
        List<Duration> durations = new ArrayList<>();
        for (int sample = 0; sample < TIMING_SAMPLES; sample++) {
            long started = System.nanoTime();
            encoder.encode(PASSWORD);
            durations.add(Duration.ofNanos(System.nanoTime() - started));
        }
        Collections.sort(durations);
        Duration median = durations.get(TIMING_SAMPLES / 2);

        System.out.println("Argon2id median hash time: " + median.toMillis() + " ms");
        assertThat(median).isLessThan(MAX_MEDIAN_HASH_TIME);
    }
}
