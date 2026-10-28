package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.api.controller.AuthApiPaths;

/**
 * Bildirim teslimi başarısız olsa bile (üretimdeki fail-closed Mail adaptörü) iş değişikliği tamamlanır; log yalnız
 * bildirim ve hata türünü yazar, alıcı adresini yazmaz.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecurityNotificationDeliveryFailureTest extends IntegrationTestSupport {

    @Test
    void teslimBasarisizOlsaDaParolaDegisirLogAliciyiYazmaz(CapturedOutput output) throws Exception {
        UUID account = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        String email = jdbc.queryForObject("select email from accounts where id = ?", String.class, account);

        mockMvc.perform(post(AuthApiPaths.PASSWORD_CHANGE).header(HttpHeaders.AUTHORIZATION, bearerFor(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"yeni-parola-yeni-parola-1","newPasswordConfirmation":"yeni-parola-yeni-parola-1"}
                                """.formatted(VALID_PASSWORD)))
                .andExpect(status().is2xxSuccessful());

        await().atMost(Duration.ofSeconds(10))
                .until(() -> output.getAll().contains("Security notification not delivered type=PASSWORD_CHANGED"));
        assertThat(output.getAll()).doesNotContain(email);
        assertThat(securityEventCount(account, "PASSWORD_CHANGED")).isEqualTo(1);
        login(email, "yeni-parola-yeni-parola-1").andExpect(status().isOk());
    }
}
