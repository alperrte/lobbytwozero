package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class DeletionConfigTest {

    private final MockEnvironment environment = new MockEnvironment();

    @Test
    void herHedefinAdresiZorunludur() {
        DeletionProperties missing = properties(Set.of("user", "game"), Map.of("user", URI.create("http://user-service:8080")));

        assertThatThrownBy(() -> new DeletionConfig(missing, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ltz.auth.deletion.service-urls.game");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://user-service:8080", "https://user.internal", "http://user-service:8080/"})
    void yalnizSemaHostVePortKabulEdilir(String url) {
        DeletionProperties valid = properties(Set.of("user"), Map.of("user", URI.create(url)));

        assertThatCode(() -> new DeletionConfig(valid, environment)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://user-service:8080/api", "http://admin:secret@user-service:8080",
            "http://user-service:8080?x=1", "http://user-service:8080#frag", "ftp://user-service", "user-service:8080",
            "/relative"})
    void yolKimlikBilgisiVeSorguIcerenAdresReddedilir(String url) {
        DeletionProperties invalid = properties(Set.of("user"), Map.of("user", URI.create(url)));

        assertThatThrownBy(() -> new DeletionConfig(invalid, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ltz.auth.deletion.service-urls.user")
                .hasMessageNotContaining("secret");
    }

    @Test
    void prodProfilindeSifresizHedefAdresiServisiAcmazMesajTokenIcermez() {
        environment.setActiveProfiles("prod");
        DeletionProperties plain = properties(Set.of("user"), Map.of("user", URI.create("http://user-service:8080")),
                "https://app.example/account/delete");

        assertThatThrownBy(() -> new DeletionConfig(plain, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ltz.auth.deletion.service-urls.user")
                .hasMessageContaining("https");
    }

    @Test
    void prodProfilindeHttpsHedefAdresiKabulEdilirDevdeHttpDeKabulEdilir() {
        environment.setActiveProfiles("prod");
        DeletionProperties secure = properties(Set.of("user"), Map.of("user", URI.create("https://user.internal")),
                "https://app.example/account/delete");
        assertThatCode(() -> new DeletionConfig(secure, environment)).doesNotThrowAnyException();

        MockEnvironment dev = new MockEnvironment();
        DeletionProperties plain = properties(Set.of("user"), Map.of("user", URI.create("http://user-service:8080")));
        assertThatCode(() -> new DeletionConfig(plain, dev)).doesNotThrowAnyException();
    }

    private static DeletionProperties properties(Set<String> targets, Map<String, URI> serviceUrls) {
        return properties(targets, serviceUrls, "http://localhost:5001/account/delete");
    }

    private static DeletionProperties properties(Set<String> targets, Map<String, URI> serviceUrls, String linkBaseUrl) {
        return new DeletionProperties(targets, serviceUrls, Duration.ofSeconds(5), Duration.ofMinutes(30),
                Duration.ofSeconds(60), Duration.ofMinutes(10), URI.create(linkBaseUrl),
                false, Duration.ofSeconds(30), 20, Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofHours(6), 10,
                Duration.ofDays(14));
    }
}
