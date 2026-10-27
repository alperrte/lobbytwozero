package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.ltz.auth.AccountDeletionTestSupport;
import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.FakeAccountDataEraserConfiguration;
import com.ltz.auth.FakeAccountDataEraserConfiguration.FakeEraser;
import com.ltz.auth.RecordingMailPortConfiguration;

/** Servis koordinasyonu: hedef servisler silmeyi onayladıkça ilerler, kısmi hata yeniden denenir, tek tamamlama. */
@Import({RecordingMailPortConfiguration.class, FakeAccountDataEraserConfiguration.class})
class AccountDeletionCoordinatorTest extends AccountDeletionTestSupport {

    private static final List<String> TARGETS = List.of("user", "mail");

    @Autowired
    private FakeEraser eraser;

    @Test
    void tumServislerOnaylayincaAuthtaSilinenKullaniciyaAitHicbirKayitKalmaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        jdbc.update("insert into external_identities (id, account_id, provider, provider_subject, linked_at) "
                + "values (gen_random_uuid(), ?, 'GOOGLE', ?, now())", accountId, "g-" + uniqueSuffix());
        loginSession(email);
        deleteAccountConfirmed(email);
        // İkinci faktör sırrı da silinmeli (silme isteği adımı geçtikten sonra eklenir).
        enableTotp(accountId);

        assertThat(coordinator.processDue()).isEqualTo(TARGETS.size());

        for (String service : TARGETS) {
            assertThat(eraser.calls(service, accountId)).as(service).isEqualTo(1);
        }
        // Proje sahibi kararı (2026-10-28): silinen kullanıcının Auth'ta hesap satırı, olayı, görevi, talebi ve kimliği kalmaz.
        assertThat(accountExists(accountId)).isFalse();
        for (String table : List.of("external_identities", "totp_credentials", "recovery_codes", "auth_sessions",
                "email_verification_codes", "account_deletion_tokens", "account_roles", "security_events",
                "account_deletion_requests", "account_deletion_tasks")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where account_id = ?", Integer.class,
                    accountId)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from accounts where email_normalized = lower(?)", Integer.class,
                email)).as("e-posta serbest").isZero();
    }

    @Test
    void islemTekrarlandigindaIdempotentTirHicbirServisIkinciKezCagrilmaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        coordinator.processDue();

        assertThat(coordinator.processDue()).isZero();
        assertThat(coordinator.processDue()).isZero();

        for (String service : TARGETS) {
            assertThat(eraser.calls(service, accountId)).as(service).isEqualTo(1);
        }
        assertThat(accountExists(accountId)).isFalse();
    }

    @Test
    void kismiBasarisizlikHesabiSilmezSadeceBasarisizServisGeriCekilmeylTekrarlanir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        eraser.failFor("mail");
        UUID accountId = deleteAccountConfirmed(email);

        assertThat(coordinator.processDue()).isEqualTo(1);

        assertThat(taskStatus(accountId, "user")).isEqualTo("CONFIRMED");
        Map<String, Object> mail = task(accountId, "mail");
        assertThat(mail).containsEntry("status", "PENDING").containsEntry("attempts", 1)
                .containsEntry("last_error", "TEST_SERVICE_DOWN");
        assertThat(((java.sql.Timestamp) mail.get("next_attempt_at")).toInstant())
                .isAfter(Instant.now().plus(Duration.ofSeconds(30))).isBefore(Instant.now().plus(Duration.ofSeconds(90)));
        // Hesap hâlâ kapalı ama silinmiş sayılmaz; kişisel veri korunur.
        assertThat(statusOf(accountId)).isEqualTo("DELETION_REQUESTED");
        // Olay yalnız ilk başarısızlıkta yazılır (her denemede yazmak denetim tablosunu şişirir).
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_TASK_FAILED")).isEqualTo(1);

        // Geri çekilme süresi dolmadan yeniden denenmez.
        assertThat(coordinator.processDue()).isZero();
        assertThat(eraser.calls("mail", accountId)).isEqualTo(1);

        // İkinci başarısızlıkta bekleme iki katına çıkar.
        makeTaskDue(accountId, "mail");
        assertThat(coordinator.processDue()).isZero();
        Map<String, Object> second = task(accountId, "mail");
        assertThat(second).containsEntry("attempts", 2);
        assertThat(((java.sql.Timestamp) second.get("next_attempt_at")).toInstant())
                .isAfter(Instant.now().plus(Duration.ofSeconds(90))).isBefore(Instant.now().plus(Duration.ofSeconds(150)));

        // Servis düzelince yalnız bekleyen görev tamamlanır; onaylanmış servis tekrar çağrılmaz.
        eraser.recover("mail");
        makeTaskDue(accountId, "mail");
        assertThat(coordinator.processDue()).isEqualTo(1);

        assertThat(eraser.calls("user", accountId)).isEqualTo(1);
        assertThat(eraser.calls("mail", accountId)).isEqualTo(3);
        // Son görev onaylanınca Auth kaydı (olaylar dahil) temizlenir.
        assertThat(accountExists(accountId)).isFalse();
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_TASK_FAILED")).isZero();
    }

    @Test
    void eszamanliIslemcilerAyniGoreviIkiKezCalistirmazTekTamamlamaOlur() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);

        List<Integer> confirmed = ConcurrentRequests.run(4, index -> () -> coordinator.processDue());

        assertThat(confirmed.stream().mapToInt(Integer::intValue).sum()).isEqualTo(TARGETS.size());
        for (String service : TARGETS) {
            assertThat(eraser.calls(service, accountId)).as(service).isEqualTo(1);
        }
        assertThat(accountExists(accountId)).isFalse();
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_COMPLETED")).isZero();
        assertThat(pendingTasks(accountId)).isZero();
    }

    @Test
    void kiradakiGorevBaskaIslemciyeVerilmezKiraDoluncaYenidenAlinir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = deleteAccountConfirmed(email);
        // Çöken bir işlemciyi taklit: görev kiralanmış (gelecekte) ama sonuç yazılmamış.
        jdbc.update("update account_deletion_tasks set attempts = 1, next_attempt_at = now() + interval '2 minutes' "
                + "where account_id = ?", accountId);

        assertThat(coordinator.processDue()).isZero();
        assertThat(eraser.calls("user", accountId)).isZero();

        jdbc.update("update account_deletion_tasks set next_attempt_at = now() - interval '1 second' where account_id = ?",
                accountId);
        assertThat(coordinator.processDue()).isEqualTo(TARGETS.size());
        assertThat(accountExists(accountId)).isFalse();
    }

    private String statusOf(UUID accountId) {
        return jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId);
    }

    private boolean accountExists(UUID accountId) {
        return jdbc.queryForObject("select count(*) from accounts where id = ?", Integer.class, accountId) > 0;
    }

    private void makeTaskDue(UUID accountId, String service) {
        jdbc.update("update account_deletion_tasks set next_attempt_at = now() - interval '1 second' "
                + "where account_id = ? and service = ?", accountId, service);
    }

    private Map<String, Object> task(UUID accountId, String service) {
        return jdbc.queryForMap("select status, attempts, last_error, next_attempt_at from account_deletion_tasks "
                + "where account_id = ? and service = ?", accountId, service);
    }

    private String taskStatus(UUID accountId, String service) {
        return (String) task(accountId, service).get("status");
    }

}
