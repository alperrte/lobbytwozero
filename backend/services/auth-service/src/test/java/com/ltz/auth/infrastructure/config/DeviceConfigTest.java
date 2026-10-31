package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DeviceConfigTest {

    private static DeviceProperties properties(int maxActive, int dailyBindLimit) {
        return new DeviceProperties(maxActive, Duration.ofDays(365), dailyBindLimit);
    }

    @Test
    void varsayilanDegerlerKabulEdilir() {
        assertThatCode(() -> new DeviceConfig(properties(5, 10))).doesNotThrowAnyException();
    }

    @Test
    void gunlukSinirEtkinSinirayaEsitseKabulEdilir() {
        assertThatCode(() -> new DeviceConfig(properties(5, 5))).doesNotThrowAnyException();
    }

    @Test
    void gunlukSinirEtkinSinirdanKucukseServisAcilmaz() {
        DeviceProperties tooSmall = properties(5, 4);

        assertThatThrownBy(() -> new DeviceConfig(tooSmall))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("daily-bind-limit")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("365"));
    }
}
