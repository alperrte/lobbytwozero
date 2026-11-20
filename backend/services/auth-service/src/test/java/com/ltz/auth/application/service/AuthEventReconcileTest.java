package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.domain.enums.AccountStatus;

/**
 * Hesap durumu olaylarının onarımı: durumu pencere içinde değişmiş ama o duruma ait olay satırı olmayan hesabın GÜNCEL
 * durumu gerçek değişim zamanıyla yeniden yazılır; normal akışın yazdığı olay varsa, pencere ya da bekleme dışındaysa,
 * kolon boşsa ve doğrulanmamış hesapta hiçbir şey yazılmaz. İkinci tur yeni satır üretmez.
 */
@Import(RecordingMailPortConfiguration.class)
class AuthEventReconcileTest extends IntegrationTestSupport {

    private static final String TARGET = "user";

    @Autowired
    private AuthEventReconciler reconciler;

    @Autowired
    private AccountAdministrationService administration;

    @DynamicPropertySource
    static void eventTargets(DynamicPropertyRegistry registry) {
        registry.add("ltz.auth.events.targets", () -> TARGET);
        registry.add("ltz.auth.deletion.service-urls.user", () -> "http://127.0.0.1:1");
    }

    @Test
    void olaySizDegisiklikGercekZamanlaYenidenYazilirIkinciTurYazmaz() throws Exception {
        UUID account = newActive();
        Instant changedAt = ago(60);
        setStatus(account, "BANNED", changedAt);

        reconciler.reconcile();

        List<Map<String, Object>> rows = outbox(account);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("target", TARGET).containsEntry("event_type", "ACCOUNT_STATUS_CHANGED")
                .containsEntry("payload_value", "BANNED").containsEntry("status", "PENDING");
        assertThat(((Timestamp) rows.getFirst().get("occurred_at")).toInstant())
                .as("olay gerçek değişim zamanını taşır, yazım anını değil").isEqualTo(changedAt);

        reconciler.reconcile();

        assertThat(outbox(account)).as("ikinci tur aynı olayı tekrar yazmaz").hasSize(1);
    }

    @Test
    void normalAkistaYazilmisOlayVarsaYenidenYazilmaz() throws Exception {
        UUID account = newActive();
        administration.changeStatus(newActive(), account, AccountStatus.SUSPENDED, "test");
        assertThat(outbox(account)).hasSize(1);
        jdbc.update("update accounts set status_changed_at = ? where id = ?", Timestamp.from(ago(60)), account);

        reconciler.reconcile();

        assertThat(outbox(account)).hasSize(1);
    }

    @Test
    void ayniDurumdaFakatEskiOlayYeterliSayilmaz() throws Exception {
        UUID account = newActive();
        administration.changeStatus(newActive(), account, AccountStatus.SUSPENDED, "test");
        administration.changeStatus(newActive(), account, AccountStatus.ACTIVE, "test");
        // Son değişimin olay satırı kaybolmuş gibi: yalnız ilk (SUSPENDED) satır kalır.
        jdbc.update("delete from auth_event_outbox where account_id = ? and payload_value = 'ACTIVE'", account);
        jdbc.update("update accounts set status_changed_at = ? where id = ?", Timestamp.from(ago(30)), account);

        reconciler.reconcile();

        assertThat(outbox(account)).extracting(row -> row.get("payload_value")).containsExactlyInAnyOrder("SUSPENDED",
                "ACTIVE");
    }

    @Test
    void bekleSuresiVePencereDisindakiDegisiklikAtlanir() throws Exception {
        UUID fresh = newActive();
        UUID old = newActive();
        setStatus(fresh, "BANNED", Instant.now().minusSeconds(60));
        setStatus(old, "BANNED", ago(48 * 60));

        reconciler.reconcile();

        assertThat(outbox(fresh)).as("normal akışa tanınan süre dolmadı").isEmpty();
        assertThat(outbox(old)).as("pencere dışı").isEmpty();
    }

    @Test
    void kolonuBosVeDogrulanmamisHesapAtlanir() throws Exception {
        UUID legacy = newActive();
        setStatus(legacy, "BANNED", ago(60));
        jdbc.update("update accounts set status_changed_at = null where id = ?", legacy);
        String email = uniqueEmail();
        register(email, uniqueUsername(), VALID_PASSWORD);
        UUID pending = accountId(email);
        jdbc.update("update accounts set status_changed_at = ? where id = ?", Timestamp.from(ago(60)), pending);

        reconciler.reconcile();

        assertThat(outbox(legacy)).isEmpty();
        assertThat(outbox(pending)).isEmpty();
    }

    @Test
    void yenidenGonderimGuncelDurumuTasirEskiDurumuDegil() throws Exception {
        UUID account = newActive();
        setStatus(account, "SUSPENDED", ago(120));
        setStatus(account, "BANNED", ago(60));

        reconciler.reconcile();

        assertThat(outbox(account)).extracting(row -> row.get("payload_value")).containsExactly("BANNED");
    }

    // ---- Yardımcılar ----

    private UUID newActive() throws Exception {
        return accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
    }

    private static Instant ago(int minutes) {
        return Instant.now().minusSeconds(minutes * 60L).truncatedTo(ChronoUnit.MICROS);
    }

    private void setStatus(UUID account, String status, Instant changedAt) {
        jdbc.update("update accounts set status = ?, status_changed_at = ? where id = ?", status,
                Timestamp.from(changedAt), account);
    }

    private List<Map<String, Object>> outbox(UUID account) {
        return jdbc.queryForList("select target, event_type, payload_value, status, occurred_at "
                + "from auth_event_outbox where account_id = ? order by created_at, payload_value", account);
    }
}
