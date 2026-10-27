package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.ltz.auth.infrastructure.config.OAuthProperties;

class ExternalUrlsTest {

    private static ExternalUrls urls(String publicBaseUrl) {
        return new ExternalUrls(new OAuthProperties(URI.create(publicBaseUrl), URI.create("http://localhost:3000/auth"),
                Duration.ofMinutes(10), Duration.ofSeconds(5), null, null));
    }

    @Test
    void sondakiEgikCizgilerAtilipYolEklenir() {
        assertThat(urls("http://localhost:5100///").publicUrl("/api/v1/auth/x")).isEqualTo("http://localhost:5100/api/v1/auth/x");
    }

    @Test
    void egikCizgisizAdresDegismedenKullanilir() {
        assertThat(urls("https://ltz.example/gw").publicUrl("/cb")).isEqualTo("https://ltz.example/gw/cb");
    }

    @Test
    void realmSemaVeAuthorityIcerir() {
        assertThat(urls("https://ltz.example:8443/gw/").realm()).isEqualTo("https://ltz.example:8443");
    }
}
