package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.AccountDeletionTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;

/**
 * Silme için geri alma (bekleme) süresi. Test ortamında süre PT0S'tir; bu sınıf 14 günlük beklemeyi talep ve görev
 * zamanlarını doğrudan kaydırarak taklit eder (üretim varsayılanı application.properties'te P14D).
 */
@Import(RecordingMailPortConfiguration.class)
class AccountDeletionGraceTest extends AccountDeletionTestSupport {

    @Test
    void beklemeSuresindeGorevCalismazVeKimlikKorunur() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        enterGracePeriod(accountId);

        assertThat(coordinator.processDue()).isZero();

        assertThat(pendingTasks(accountId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId))
                .isEqualToIgnoringCase(email);
        assertThat(statusOf(accountId)).isEqualTo("DELETION_REQUESTED");
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_IDENTITY_ERASED")).isZero();
        // Hesap kapalıdır: doğru parolayla bile giriş yapılamaz, ama neden açıkça söylenir (iptal yolu için).
        login(email, VALID_PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_PENDING"));
    }

    @Test
    void dogruParolaylaIptalHesabiAktifEderVeGorevleriSiler() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        enterGracePeriod(accountId);

        cancel(email, VALID_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("DELETION_CANCELLED"));

        assertThat(statusOf(accountId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_tasks where account_id = ?",
                Integer.class, accountId)).isZero();
        assertThat(jdbc.queryForObject("select status from account_deletion_requests where account_id = ?",
                String.class, accountId)).isEqualTo("CANCELLED");
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_CANCELLED")).isEqualTo(1);
        assertThat(coordinator.processDue()).isZero();
        login(email, VALID_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
    }

    @Test
    void yanlisParolaVeBilinmeyenHesapAyniYanitiAlirHesapKapaliKalir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        enterGracePeriod(accountId);

        String wrong = cancel(email, "definitely-not-the-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andReturn().getResponse().getContentAsString();
        String unknown = cancel(uniqueEmail(), VALID_PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(wrong);
        assertThat(statusOf(accountId)).isEqualTo("DELETION_REQUESTED");
        assertThat(pendingTasks(accountId)).isEqualTo(2);
    }

    @Test
    void aktifHesapIcinIptalReddedilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());

        cancel(email, VALID_PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_NOT_PENDING"));
    }

    @Test
    void beklemeSuresiBitinceIptalReddedilirGorevlerBaslayincaKimlikAnonimlesir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        expireGracePeriod(accountId);

        cancel(email, VALID_PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_CANCEL_WINDOW_CLOSED"));
        assertThat(statusOf(accountId)).isEqualTo("DELETION_REQUESTED");

        // İlk görev işlendiğinde Auth PII'si, servisler onaylamasa da hemen silinir.
        coordinator.processDue();

        assertThat(jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId))
                .isEqualTo("deleted-" + accountId + "@deleted.invalid");
        assertThat(jdbc.queryForObject("select password_hash from accounts where id = ?", String.class, accountId))
                .isNull();
        assertThat(statusOf(accountId)).isEqualTo("DELETION_REQUESTED");
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_IDENTITY_ERASED")).isEqualTo(1);
        cancel(email, VALID_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void iptalSonrasiYenidenSilmeIstegiTalebiYeniBeklemeyleAcar() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        enterGracePeriod(accountId);
        cancel(email, VALID_PASSWORD).andExpect(status().isOk());
        // Yeni onay e-postası için bekleme süresi (cooldown) geçmiş sayılır.
        jdbc.update("update account_deletion_tokens set created_at = created_at - interval '5 minutes', "
                + "expires_at = expires_at - interval '5 minutes' where account_id = ?", accountId);

        String bearer = bearerFor(accountId);
        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted());
        confirmDeletion(bearer, mailPort.awaitLatestDeletionToken(email, 2), usernameOf(accountId))
                .andExpect(status().isAccepted());

        assertThat(statusOf(accountId)).isEqualTo("DELETION_REQUESTED");
        assertThat(jdbc.queryForObject("select status from account_deletion_requests where account_id = ?",
                String.class, accountId)).isEqualTo("COORDINATING");
        assertThat(pendingTasks(accountId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_requests where account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
    }

    private String statusOf(UUID accountId) {
        return jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId);
    }

    private ResultActions cancel(String identifier, String password) throws Exception {
        return postJson(AuthApiPaths.ACCOUNT_DELETION_CANCEL,
                "{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password));
    }

    /** 14 günlük beklemeyi taklit eder: talep ve görevler ileri bir ana ertelenir. */
    private void enterGracePeriod(UUID accountId) {
        jdbc.update("update account_deletion_requests set scheduled_for = now() + interval '14 days' "
                + "where account_id = ?", accountId);
        jdbc.update("update account_deletion_tasks set next_attempt_at = now() + interval '14 days' "
                + "where account_id = ?", accountId);
    }

    /** Beklemenin bittiği durumu taklit eder: talep geçmişe alınır, görevler vadesi gelmiş kalır. */
    private void expireGracePeriod(UUID accountId) {
        jdbc.update("update account_deletion_requests set requested_at = now() - interval '15 days', "
                + "scheduled_for = now() - interval '1 hour' where account_id = ?", accountId);
        jdbc.update("update account_deletion_tasks set next_attempt_at = now() - interval '1 hour' "
                + "where account_id = ?", accountId);
    }
}
