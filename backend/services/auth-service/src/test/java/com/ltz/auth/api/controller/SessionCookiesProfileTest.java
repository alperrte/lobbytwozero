package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.ltz.auth.infrastructure.config.SessionProperties;

class SessionCookiesProfileTest {

    private static SessionProperties properties(boolean secure) {
        return new SessionProperties(Duration.ofDays(7), Duration.ofDays(30), Duration.ofSeconds(10), secure);
    }

    private static MockEnvironment profile(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void secureOlmayanCerezYalnizDevVeTestProfilindeKabulEdilir() {
        assertThat(new SessionCookies(properties(false), profile("dev")).accessCookieName()).isEqualTo("ltz_access");
        assertThat(new SessionCookies(properties(false), profile("test")).accessCookieName()).isEqualTo("ltz_access");
    }

    @Test
    void secureOlmayanCerezDigerProfillerdeReddedilir() {
        SessionProperties insecure = properties(false);
        MockEnvironment prod = profile("prod");
        MockEnvironment production = profile("production");
        MockEnvironment noProfile = profile();
        MockEnvironment devAndProd = profile("dev", "prod");

        assertThatThrownBy(() -> new SessionCookies(insecure, prod))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SessionCookies(insecure, production))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SessionCookies(insecure, noProfile))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SessionCookies(insecure, devAndProd))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secureCerezHerProfildeKabulEdilir() {
        assertThat(new SessionCookies(properties(true), profile("prod")).accessCookieName()).isEqualTo("__Host-ltz_access");
    }
}
