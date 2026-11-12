package com.ltz.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.ltz.auth.IntegrationTestSupport;

class FlywayMigrationTest extends IntegrationTestSupport {

    private static final String HASH = "a".repeat(64);

    @Test
    void authMigrationlariKendiAraligindaBasariylaUygulanir() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).containsExactly("61", "62", "63", "64", "65", "66", "67");
    }

    @Test
    void yasDogrulamaTablosuKisitlariniVeSilmedeCascadeiUygular() throws Exception {
        // Doğrudan SQL hesabı: kayıt akışı güvenlik olayını arka planda yazar ve silmeyle yarışırdı.
        UUID accountId = UUID.randomUUID();
        insertAccount(accountId, "ACTIVE");
        jdbc.update("insert into account_age_verifications (account_id, verified_at, method) "
                + "values (?, now(), 'SELF_DECLARED')", accountId);

        assertThatThrownBy(() -> jdbc.update("update account_age_verifications set method = 'ID_CARD' "
                + "where account_id = ?", accountId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update account_age_verifications set revoked_at = now() "
                + "where account_id = ?", accountId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update account_age_verifications set revoked_at = now(), "
                + "revoked_by = 'BOT' where account_id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("delete from accounts where id = ?", accountId);
        assertThat(jdbc.queryForObject("select count(*) from account_age_verifications where account_id = ?",
                Integer.class, accountId)).isZero();
    }

    @Test
    void migrationBesKanonikRoluSeedEder() {
        assertThat(jdbc.queryForList("select code from roles", String.class))
                .containsExactlyInAnyOrder("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN");
    }

    @Test
    void tanimsizRolKoduReddedilir() {
        UUID roleId = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update("insert into roles (id, code) values (?, 'SUPER_USER')", roleId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tanimsizHesapDurumuReddedilir() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> insertAccount(accountId, "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void normalizeEdilmemisEPostaReddedilir() {
        String suffix = uniqueSuffix();
        UUID accountId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update("""
                insert into accounts (id, email, email_normalized, username, username_normalized, status,
                                      created_at, updated_at)
                values (?, ?, ?, ?, ?, 'PENDING_VERIFICATION', now(), now())
                """, accountId, "Mixed@Example.test", "Mixed." + suffix + "@Example.test",
                "u" + suffix, "u" + suffix))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void hesapBasinaYalnizBirAktifKodOlabilir() {
        UUID accountId = UUID.randomUUID();
        insertAccount(accountId, "PENDING_VERIFICATION");
        insertCode(accountId, HASH);

        assertThatThrownBy(() -> insertCode(accountId, HASH))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acikKodBicimindekiDegerOzetSutunundaReddedilir() {
        UUID accountId = UUID.randomUUID();
        insertAccount(accountId, "PENDING_VERIFICATION");

        assertThatThrownBy(() -> insertCode(accountId, "123456"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void epostasizAktifHesapKabulEdilirVeBirdenFazlaBosEpostaCakismaz() {
        String first = "s" + uniqueSuffix();
        String second = "s" + uniqueSuffix();

        insertAccountWithoutEmail(UUID.randomUUID(), first, "ACTIVE", false);
        insertAccountWithoutEmail(UUID.randomUUID(), second, "ACTIVE", false);

        assertThat(jdbc.queryForObject("select count(*) from accounts where email is null and username_normalized in (?, ?)",
                Integer.class, first, second)).isEqualTo(2);
    }

    @Test
    void epostaIleNormalizeEdilmisEpostaBirlikteDoluYaDaBirlikteBosOlmali() {
        String suffix = uniqueSuffix();

        assertThatThrownBy(() -> jdbc.update("""
                insert into accounts (id, email, email_normalized, username, username_normalized, status, created_at, updated_at)
                values (?, ?, null, ?, ?, 'ACTIVE', now(), now())
                """, UUID.randomUUID(), suffix + "@example.test", "u" + suffix, "u" + suffix))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void epostasizHesapDogrulamaBeklemezVeDogrulanmisSayilmaz() {
        assertThatThrownBy(() -> insertAccountWithoutEmail(UUID.randomUUID(), "p" + uniqueSuffix(), "PENDING_VERIFICATION", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAccountWithoutEmail(UUID.randomUUID(), "v" + uniqueSuffix(), "ACTIVE", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void googleKayitTokeniYalnizOzetOlarakSaklanirSuresiGecerlidir() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into google_registrations (token_hash, subject, email, created_at, expires_at)
                values ('ham-token', 's', 'e@example.test', now(), now() + interval '5 minutes')
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into google_registrations (token_hash, subject, email, created_at, expires_at)
                values (?, 's', 'e@example.test', now(), now() - interval '1 minute')
                """, HASH)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertAccountWithoutEmail(UUID id, String username, String status, boolean verified) {
        jdbc.update("""
                insert into accounts (id, email, email_normalized, username, username_normalized, status,
                                      email_verified_at, created_at, updated_at)
                values (?, null, null, ?, ?, ?, ?, now(), now())
                """, id, username, username, status, verified ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
    }

    private void insertAccount(UUID id, String status) {
        String suffix = uniqueSuffix();
        jdbc.update("""
                insert into accounts (id, email, email_normalized, username, username_normalized, status,
                                      created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, now(), now())
                """, id, suffix + "@example.test", suffix + "@example.test", "u" + suffix, "u" + suffix, status);
    }

    private void insertCode(UUID accountId, String hash) {
        jdbc.update("""
                insert into email_verification_codes (id, account_id, purpose, code_hash, created_at, expires_at)
                values (?, ?, 'EMAIL_VERIFICATION', ?, now(), now() + interval '10 minutes')
                """, UUID.randomUUID(), accountId, hash);
    }
}
