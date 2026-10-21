package com.ltz.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;

import com.ltz.auth.IntegrationTestSupport;

class FlywayMigrationTest extends IntegrationTestSupport {

    private static final String HASH = "a".repeat(64);

    @Test
    void authMigrationlariKendiAraligindaBasariylaUygulanir() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).containsExactly("61", "62", "63", "64", "65", "66", "67", "68");
    }

    /** Diğer testler rol ekleyebildiği için tablo yerine migration içeriği denetlenir. */
    @Test
    void migrationYalnizPlayerRoluSeedEder() throws Exception {
        String seedSql = new ClassPathResource("db/migration/V61__create_account_tables.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(jdbc.queryForList("select code from roles", String.class)).contains("PLAYER");
        assertThat(seedSql.lines().filter(line -> line.startsWith("INSERT INTO roles")))
                .containsExactly("INSERT INTO roles (id, code) VALUES (gen_random_uuid(), 'PLAYER');");
    }

    @Test
    void tanimsizRolKoduReddedilir() {
        assertThatThrownBy(() -> jdbc.update("insert into roles (id, code) values (?, 'SUPER_USER')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tanimsizHesapDurumuReddedilir() {
        assertThatThrownBy(() -> insertAccount(UUID.randomUUID(), "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void normalizeEdilmemisEPostaReddedilir() {
        String suffix = uniqueSuffix();
        assertThatThrownBy(() -> jdbc.update("""
                insert into accounts (id, email, email_normalized, username, username_normalized, status,
                                      created_at, updated_at)
                values (?, ?, ?, ?, ?, 'PENDING_VERIFICATION', now(), now())
                """, UUID.randomUUID(), "Mixed@Example.test", "Mixed." + suffix + "@Example.test",
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
