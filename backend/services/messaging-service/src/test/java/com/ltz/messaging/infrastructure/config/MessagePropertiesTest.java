package com.ltz.messaging.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Herkesten silme süresi (MSG-26) pozitif olmalı: sıfır ya da negatif süre uygulamayı başlatmaz. */
class MessagePropertiesTest {

    @Test
    void herkestenSilmeSuresiPozitifOlmali() {
        assertThat(properties(Duration.ofHours(48)).deleteForEveryoneWindow()).isEqualTo(Duration.ofHours(48));
        assertThatThrownBy(() -> properties(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    private static MessageProperties properties(Duration window) {
        return new MessageProperties(30, 30, 100, 20, 50, window);
    }
}
