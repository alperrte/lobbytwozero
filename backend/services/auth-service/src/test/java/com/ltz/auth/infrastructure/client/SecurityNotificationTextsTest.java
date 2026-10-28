package com.ltz.auth.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ltz.auth.application.port.SecurityNotificationType;

class SecurityNotificationTextsTest {

    private static final Instant WHEN = Instant.parse("2026-10-29T10:15:00Z");

    @ParameterizedTest
    @EnumSource(SecurityNotificationType.class)
    void herTurIcinIkiDilliKonuVeGovdeUretilirBaglantiTasimaz(SecurityNotificationType type) {
        String subject = SecurityNotificationTexts.subject(type);
        String body = SecurityNotificationTexts.body(type, WHEN);

        assertThat(subject).contains(" / ").startsWith("LTZ");
        assertThat(body).contains("2026-10-29 10:15 UTC").contains("Bu işlemi siz yapmadıysanız")
                .contains("If you did not do this");
        assertThat(body).as("güvenlik bildirimi tıklanacak bağlantı, token veya parola içermez")
                .doesNotContain("http").doesNotContain("token").doesNotContain("#");
    }

    @Test
    void epostaDegisimiBildirimiEskiAdrese_gittiginiSoyler() {
        assertThat(SecurityNotificationTexts.body(SecurityNotificationType.EMAIL_CHANGED, WHEN))
                .contains("eski adrese").contains("old address");
    }
}
