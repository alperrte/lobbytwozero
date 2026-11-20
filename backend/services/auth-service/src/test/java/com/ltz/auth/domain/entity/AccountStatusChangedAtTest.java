package com.ltz.auth.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ltz.auth.domain.enums.AccountStatus;

/** {@code statusChangedAt} yalnız durum geçişlerinde ilerler; parola, e-posta ve token sürümü onu oynatmaz. */
class AccountStatusChangedAtTest {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);

    private Account pending() {
        return Account.registerPending("kisi@ornek.test", "kisi_1", "hash", new Role(), T0);
    }

    @Test
    void ilkDurumKayitAnindaYazilir() {
        assertThat(pending().getStatusChangedAt()).isEqualTo(T0);
        assertThat(Account.registerSteam("steam_1", new Role(), T0).getStatusChangedAt()).isEqualTo(T0);
    }

    @Test
    void epostaDogrulamaDurumGecisidir() {
        Account account = pending();

        account.markEmailVerified(T1);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getStatusChangedAt()).isEqualTo(T1);
    }

    @Test
    void yoneticiDurumDegisikligiSilmeTalebiVeVazgecmeAnlariniYazar() {
        Account account = pending();
        account.markEmailVerified(T0);

        account.changeStatus(AccountStatus.SUSPENDED, T1);
        assertThat(account.getStatusChangedAt()).isEqualTo(T1);

        account.changeStatus(AccountStatus.ACTIVE, T2);
        account.requestDeletion(T2.plusSeconds(1));
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETION_REQUESTED);
        assertThat(account.getStatusChangedAt()).isEqualTo(T2.plusSeconds(1));

        account.cancelDeletion(T2.plusSeconds(2));
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getStatusChangedAt()).isEqualTo(T2.plusSeconds(2));
    }

    @Test
    void durumDisiDegisiklikDegisimAniniOynatmaz() {
        Account account = pending();
        account.markEmailVerified(T0);

        account.changePassword("yeni-hash", T1);
        account.invalidateAllTokens(T2);
        account.changeEmail("yeni@ornek.test", T2);

        assertThat(account.getUpdatedAt()).isEqualTo(T2);
        assertThat(account.getStatusChangedAt()).as("durum değişmedi").isEqualTo(T0);
    }

    @Test
    void gecersizGecisDegisimAniniOynatmaz() {
        Account account = pending();

        try {
            account.changeStatus(AccountStatus.PENDING_VERIFICATION, T1);
        } catch (IllegalStateException expected) {
            // izin verilmeyen geçiş
        }

        assertThat(account.getStatusChangedAt()).isEqualTo(T0);
    }
}
