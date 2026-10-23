package com.ltz.auth.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ltz.auth.IntegrationTestSupport;

/** dev profili, SMTP sunucusu erişilemez: kayıt tamamlanır ama teslim başarılı sayılmaz. */
@ActiveProfiles("dev")
class DevSmtpDeliveryFailureTest extends IntegrationTestSupport {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(15);

    @DynamicPropertySource
    static void unreachableSmtp(DynamicPropertyRegistry registry) {
        int closedPort = freePort();
        registry.add("ltz.auth.mail.dev-smtp.host", () -> "127.0.0.1");
        registry.add("ltz.auth.mail.dev-smtp.port", () -> closedPort);
        registry.add("ltz.auth.mail.dev-smtp.connect-timeout", () -> "PT1S");
    }

    @Test
    void smtpBaglantisiBasarisizsaKodTeslimEdilemediOlarakGecersizKilinir() throws Exception {
        String email = uniqueEmail();

        register(email, uniqueUsername(), VALID_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"nextStep\":\"VERIFY_EMAIL\"}", true));

        UUID accountId = accountId(email);
        await().atMost(DELIVERY_TIMEOUT).until(() -> securityEventCount(accountId,
                "VERIFICATION_CODE_DELIVERY_FAILED") == 1);
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where account_id = ? and consumed_at is null and invalidated_at is null
                """, Integer.class, accountId)).isZero();
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");
    }

    // Port bağlanıp hemen bırakılır; test süresince dinleyen bir SMTP sunucusu yoktur.
    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
