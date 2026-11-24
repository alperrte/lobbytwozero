package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Parola sıfırlama bağlantısı koruması (C076): prod'da https zorunlu, geliştirmede yerel http serbest. */
class PasswordResetConfigTest {

    private static PasswordResetProperties properties(String linkBaseUrl) {
        return new PasswordResetProperties(Duration.ofMinutes(30), Duration.ofSeconds(60), URI.create(linkBaseUrl));
    }

    private static MockEnvironment profile(String name) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(name);
        return environment;
    }

    @Test
    void prodProfilindeHttpsBaglantiKabulEdilir() {
        assertThatCode(() -> new PasswordResetConfig(properties("https://ltz.example/sifre-sifirla"), profile("prod")))
                .doesNotThrowAnyException();
    }

    @Test
    void prodProfilindeHttpBaglantiAcilisiDurdurur() {
        assertThatThrownBy(() -> new PasswordResetConfig(properties("http://ltz.example/sifre-sifirla"), profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("link-base-url");
    }

    @Test
    void prodDisindaYerelHttpBaglantiyaIzinVerilir() {
        assertThatCode(() -> new PasswordResetConfig(properties("http://localhost:5001/sifre-sifirla"), profile("dev")))
                .doesNotThrowAnyException();
    }
}
