package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.AccountDeletionTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;

/**
 * Hesap silme talebi ve vazgeçme, veri sahibi servislere (User) {@code ACCOUNT_STATUS_CHANGED} olarak bildirilir:
 * talepte {@code DELETION_REQUESTED}, yalnız başarılı vazgeçmede {@code ACTIVE}. Teslim bu testin konusu değildir;
 * outbox satırları incelenir (işçi kapalı).
 */
@Import(RecordingMailPortConfiguration.class)
class AccountDeletionStatusEventTest extends AccountDeletionTestSupport {

    private static final String EVENT_TYPE = "ACCOUNT_STATUS_CHANGED";

    @DynamicPropertySource
    static void eventTargets(DynamicPropertyRegistry registry) {
        registry.add("ltz.auth.events.targets", () -> "user");
        registry.add("ltz.auth.deletion.service-urls.user", () -> "http://127.0.0.1:1");
    }

    @Test
    void silmeOnayiDeletionRequestedDurumOlayiYazar() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());

        UUID accountId = deleteAccountConfirmed(email);

        assertThat(statusEvents(accountId)).containsExactly("DELETION_REQUESTED");
    }

    @Test
    void basariliVazgecmeActiveDurumOlayiYazar() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        enterGracePeriod(accountId);

        cancel(email, VALID_PASSWORD).andExpect(status().isOk());

        assertThat(statusEvents(accountId)).containsExactly("DELETION_REQUESTED", "ACTIVE");
    }

    @Test
    void basarisizVazgecmeOlayYazmaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        enterGracePeriod(accountId);

        cancel(email, "definitely-not-the-password").andExpect(status().isUnauthorized());

        assertThat(statusEvents(accountId)).containsExactly("DELETION_REQUESTED");
    }

    @Test
    void aktifHesabinReddedilenVazgecmesiOlayYazmaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);

        cancel(email, VALID_PASSWORD).andExpect(status().isConflict());

        assertThat(statusEvents(accountId)).isEmpty();
    }

    /** Hesabın durum olaylarının yükleri, yazılma sırasıyla. */
    private List<String> statusEvents(UUID accountId) {
        return jdbc.queryForList("select payload_value from auth_event_outbox where account_id = ? "
                + "and event_type = ? order by created_at, occurred_at", String.class, accountId, EVENT_TYPE);
    }

    /** Test ortamında bekleme süresi 0'dır; 14 günlük pencereyi talep ve görevleri kaydırarak taklit eder. */
    private void enterGracePeriod(UUID accountId) {
        jdbc.update("update account_deletion_requests set scheduled_for = now() + interval '14 days' "
                + "where account_id = ?", accountId);
        jdbc.update("update account_deletion_tasks set next_attempt_at = now() + interval '14 days' "
                + "where account_id = ?", accountId);
    }

    private ResultActions cancel(String identifier, String password) throws Exception {
        return postJson(AuthApiPaths.ACCOUNT_DELETION_CANCEL,
                "{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password));
    }
}
