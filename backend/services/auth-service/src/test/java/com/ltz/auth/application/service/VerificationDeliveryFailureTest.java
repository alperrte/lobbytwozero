package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.auth.IntegrationTestSupport;

/**
 * Üretim adaptörüyle (Mail Service teslimi henüz yok) davranış: kayıt tamamlanır ama kod "gönderildi"
 * sayılmaz, DELIVERY_FAILED olarak geçersiz kılınır; yeniden gönderme yeni bir kod üretir.
 */
class VerificationDeliveryFailureTest extends IntegrationTestSupport {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void mailServiceKullanilamazsaKodGecersizKilinirYanitGonderimIddiaEtmez() throws Exception {
        String email = uniqueEmail();

        register(email, uniqueUsername(), VALID_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"nextStep\":\"VERIFY_EMAIL\"}", true));

        UUID accountId = accountId(email);
        awaitFailedDeliveries(accountId, 1);
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");
        assertThat(securityEventCount(accountId, "VERIFICATION_CODE_DELIVERY_FAILED")).isEqualTo(1);
        assertThat(activeCodeCount(accountId)).isZero();
    }

    @Test
    void basarisizGonderimdenSonraYenidenGondermeYeniKodUretir() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        UUID accountId = accountId(email);
        awaitFailedDeliveries(accountId, 1);
        shiftCodesBack(accountId, "2 minutes");

        resend(email).andExpect(status().isAccepted());

        awaitFailedDeliveries(accountId, 2);
        assertThat(jdbc.queryForObject("select count(distinct code_hash) from email_verification_codes "
                + "where account_id = ?", Integer.class, accountId)).isEqualTo(2);
    }

    private void awaitFailedDeliveries(UUID accountId, int expected) {
        await().atMost(DELIVERY_TIMEOUT).until(() -> jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where account_id = ? and invalidation_reason = 'DELIVERY_FAILED'
                """, Integer.class, accountId) == expected);
    }

    private int activeCodeCount(UUID accountId) {
        return jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where account_id = ? and consumed_at is null and invalidated_at is null
                """, Integer.class, accountId);
    }
}
