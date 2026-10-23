package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class CleanupConfigTest {

    private static final Duration ASSERTION_MAX_AGE = Duration.ofMinutes(5);
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final OAuthProperties oauth = oauthWithAssertionAge();
    private final AttemptProperties attempts = new AttemptProperties(5, ATTEMPT_WINDOW, LOCK_DURATION);

    @Test
    void tutarliSurelerKabulEdilir() {
        assertThatCode(() -> new CleanupConfig(cleanup(Duration.ofHours(1), Duration.ofHours(1)), oauth, attempts))
                .doesNotThrowAnyException();
    }

    @Test
    void nonceSaklamaSuresiReplayPenceresindenKisaysaBaslamaz() {
        CleanupProperties shortNonceRetention = cleanup(ASSERTION_MAX_AGE, Duration.ofHours(1));

        assertThatThrownBy(() -> new CleanupConfig(shortNonceRetention, oauth, attempts))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nonce-retention");
    }

    @Test
    void sayacSaklamaSuresiKilitSuresiniKapsamazsaBaslamaz() {
        CleanupProperties shortThrottleRetention = cleanup(Duration.ofHours(1), Duration.ofMinutes(20));

        assertThatThrownBy(() -> new CleanupConfig(shortThrottleRetention, oauth, attempts))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("throttle-retention");
    }

    @Test
    void guvenlikOlayiSaklamaSuresiVarsayilanOlarakKapali() {
        assertThat(cleanup(Duration.ofHours(1), Duration.ofHours(1)).securityEventRetentionConfigured()).isFalse();
    }

    private static CleanupProperties cleanup(Duration nonceRetention, Duration throttleRetention) {
        return new CleanupProperties(true, Duration.ofHours(1), 1000, Duration.ofHours(24), Duration.ofHours(1),
                nonceRetention, throttleRetention, Duration.ofDays(2), Duration.ofDays(7), null);
    }

    private static OAuthProperties oauthWithAssertionAge() {
        OAuthProperties properties = mock(OAuthProperties.class);
        when(properties.steam()).thenReturn(new OAuthProperties.Steam(URI.create("https://steam.example/openid/login"),
                ASSERTION_MAX_AGE, Duration.ofMinutes(15), Duration.ofMinutes(30)));
        return properties;
    }
}
