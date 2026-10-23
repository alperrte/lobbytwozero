package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import com.ltz.auth.AccountDeletionTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;

/**
 * Üretim adaptörüyle (gerçek HTTP + servis token'ı), hedef servisler silmeyi onaylamazsa (burada 503) silme asla
 * tamamlanmış sayılmaz. Görevler PENDING kalır ve geri çekilmeyle yeniden denenir; hesabın kişisel verisi korunur,
 * hesap kapalıdır.
 */
@Import(RecordingMailPortConfiguration.class)
class AccountDeletionUnavailableServicesTest extends AccountDeletionTestSupport {

    @Test
    void servisSilmeyiOnaylayamazsaHesapDeletedOlmazGorevlerPendingKalir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);

        assertThat(coordinator.processDue()).isZero();

        assertThat(pendingTasks(accountId)).isEqualTo(2);
        assertThat(jdbc.queryForList("select distinct last_error from account_deletion_tasks where account_id = ?",
                String.class, accountId)).containsExactly("SERVICE_ERASURE_HTTP_503");
        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .isEqualTo("DELETION_REQUESTED");
        assertThat(jdbc.queryForObject("select status from account_deletion_requests where account_id = ?",
                String.class, accountId)).isEqualTo("COORDINATING");
        assertThat(jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId))
                .isEqualToIgnoringCase(email);
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_COMPLETED")).isZero();
    }
}
