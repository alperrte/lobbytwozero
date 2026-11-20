package com.ltz.messaging.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Sesli mesaj süre sınırları (MSG-21) DB CHECK'i (500–121000 ms) dışına çıkamaz. */
class VoicePropertiesTest {

    @Test
    void varsayilanSinirlarGecerlidir() {
        VoiceProperties properties = new VoiceProperties(Duration.ofMillis(500), Duration.ofMinutes(2),
                Duration.ofSeconds(1));
        assertThat(properties.maxAcceptedMillis()).isEqualTo(121_000);
        assertThat(properties.minDuration()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void sinirlarVeritabaniKuraliniAsamaz() {
        assertRejected(Duration.ofMillis(500), Duration.ofMinutes(2), Duration.ofSeconds(2));
        assertRejected(Duration.ofMillis(100), Duration.ofMinutes(2), Duration.ofSeconds(1));
        assertRejected(Duration.ofSeconds(3), Duration.ofSeconds(2), Duration.ZERO);
        assertRejected(Duration.ofMillis(500), Duration.ofMinutes(2), Duration.ofSeconds(-1));
    }

    private static void assertRejected(Duration min, Duration max, Duration tolerance) {
        assertThatThrownBy(() -> new VoiceProperties(min, max, tolerance))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
