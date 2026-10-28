package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.application.port.PasswordResetMail;
import com.ltz.auth.application.port.PasswordResetMailPort;

/**
 * Üretim adaptörüyle (Mail Service teslimi yok): yanıt gönderim iddia etmez, token DELIVERY_FAILED olur;
 * yeni istek (bekleme sonrası) yeni token üretir.
 */
class PasswordResetDeliveryFailureTest extends IntegrationTestSupport {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void teslimEdilemeyenTokenGecersizKilinirYanitDegismez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);

        postJson(AuthApiPaths.PASSWORD_FORGOT, "{\"email\":\"%s\"}".formatted(email))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"nextStep\":\"CHECK_EMAIL\"}", true));

        await().atMost(DELIVERY_TIMEOUT).until(() -> failedTokens(accountId) == 1);
        assertThat(securityEventCount(accountId, "PASSWORD_RESET_DELIVERY_FAILED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where account_id = ? "
                + "and consumed_at is null and invalidated_at is null", Integer.class, accountId)).isZero();

        jdbc.update("update password_reset_tokens set created_at = created_at - interval '2 minutes', "
                + "expires_at = expires_at - interval '2 minutes' where account_id = ?", accountId);
        postJson(AuthApiPaths.PASSWORD_FORGOT, "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isAccepted());
        await().atMost(DELIVERY_TIMEOUT).until(() -> failedTokens(accountId) == 2);
    }

    @Test
    void parolaSifirlamaEpostasiMaskelenmisGosterilir() {
        PasswordResetMail mail = new PasswordResetMail(UUID.randomUUID(), UUID.randomUUID(), "user@example.test",
                "https://ltz.example.test/auth/reset-password#token=secret-token", Duration.ofMinutes(30));

        assertThat(mail.toString()).doesNotContain("user@example.test").doesNotContain("secret-token");
        assertThat(PasswordResetMailPort.class).isInterface();
    }

    private int failedTokens(UUID accountId) {
        return jdbc.queryForObject("select count(*) from password_reset_tokens where account_id = ? "
                + "and invalidation_reason = 'DELIVERY_FAILED'", Integer.class, accountId);
    }
}
