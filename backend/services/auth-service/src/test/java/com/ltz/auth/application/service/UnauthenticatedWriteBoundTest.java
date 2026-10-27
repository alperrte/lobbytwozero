package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;

/**
 * B9: kimliksiz uçlar sınırsız satır üretmemeli. Hesabı olmayan olaylar (bilinmeyen e-posta ile giriş denemeleri vb.)
 * tabloya yazılmaz; kilit devredeyken tekrarlanan istekler olay yazmaz.
 */
@Import(RecordingMailPortConfiguration.class)
class UnauthenticatedWriteBoundTest extends IntegrationTestSupport {

    private static final int REPEATED_REQUESTS = 12;

    @Test
    void hesapsizBasarisizGirisler_guvenlikOlayiTablosunuSisirmez() throws Exception {
        int before = jdbc.queryForObject("select count(*) from security_events where account_id is null",
                Integer.class);

        for (int attempt = 0; attempt < REPEATED_REQUESTS; attempt++) {
            login(uniqueEmail(), "wrong-password-value").andExpect(status().isUnauthorized());
        }

        assertThat(jdbc.queryForObject("select count(*) from security_events where account_id is null",
                Integer.class)).isEqualTo(before);
    }

    @Test
    void kilitliHesaptaTekrarlananIstekler_istekBasinaOlayYazmaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        for (int attempt = 0; attempt < REPEATED_REQUESTS; attempt++) {
            login(email, "wrong-password-value");
        }

        assertThat(securityEventCount(accountId(email), "ATTEMPTS_THROTTLED")).isZero();
        assertThat(securityEventCount(accountId(email), "LOGIN_FAILED")).isLessThanOrEqualTo(REPEATED_REQUESTS);
    }
}
