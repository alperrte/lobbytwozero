package com.ltz.mail.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PlatformPropertiesTest {

    @Test
    void noCallersMeansTheGateIsClosed() {
        PlatformProperties platform = new PlatformProperties(null);

        assertThat(platform.callers()).isEmpty();
        assertThat(platform.subjects()).isEmpty();
        assertThat(platform.callerFor("svc:social-service")).isNull();
    }

    @Test
    void limitsDefaultToFivePerAccountAndFiveHundredPerCaller() {
        PlatformProperties platform = new PlatformProperties(Map.of("social-service", new PlatformProperties.Caller(null, null)));

        assertThat(platform.callerFor("svc:social-service").accountPerHour()).isEqualTo(5);
        assertThat(platform.callerFor("svc:social-service").callerPerHour()).isEqualTo(500);
        assertThat(platform.subjects()).containsExactly("svc:social-service");
    }

    @Test
    void onlyRegisteredServiceSubjectsAreRecognised() {
        PlatformProperties platform = new PlatformProperties(Map.of("social-service", new PlatformProperties.Caller(2, 20)));

        assertThat(platform.callerFor("svc:social-service").accountPerHour()).isEqualTo(2);
        assertThat(platform.callerFor("svc:other-service")).isNull();
        assertThat(platform.callerFor("social-service")).isNull();
        assertThat(platform.callerFor("svc:auth-service")).isNull();
        assertThat(platform.callerFor(null)).isNull();
    }

    @Test
    void rejectsBadCallerNamesAndNonPositiveLimits() {
        PlatformProperties.Caller caller = new PlatformProperties.Caller(1, 1);

        Map<String, PlatformProperties.Caller> authService = Map.of("auth-service", caller);
        Map<String, PlatformProperties.Caller> badCase = Map.of("Social_Service", caller);

        assertThatThrownBy(() -> new PlatformProperties(authService)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformProperties(badCase)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformProperties.Caller(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformProperties.Caller(5, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
