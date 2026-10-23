package com.ltz.game.infrastructure.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static com.ltz.game.infrastructure.persistence.MigrationTransferSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MigrationTransferTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @Test void allApplicationDataSeedAndBothHistoryIdentitiesArePreserved() throws Exception {
        var source = source();
        var target = database(POSTGRES, CURRENT);
        var originalHistory = history(source);
        var freshHistory = history(target);
        var sourceDigests = digests(source);
        assertEquals(21, sourceDigests.size());
        assertTrue(sourceDigests.values().stream().allMatch(t -> t.rows() > 0));
        Receipt receipt = transfer(source, target, (connection, count) -> { });
        requireReady(source, target, receipt);
        assertEquals(sourceDigests, digests(target));
        assertEquals(originalHistory, history(source));
        assertEquals(freshHistory, history(target));
        assertEquals(13, originalHistory.size());
        assertEquals(10, freshHistory.size());
        var jdbc = new JdbcTemplate(target);
        assertEquals(19L, jdbc.queryForObject("select version from game_provider_sync_state where provider='STEAM'", Long.class));
        assertEquals(991231L, jdbc.queryForObject("select cursor from game_provider_sync_state where provider='STEAM'", Long.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from game_developer_grants where revoked_at is null", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from game_developer_grants where revoked_at is not null", Integer.class));
        assertThrows(DataAccessException.class, () -> jdbc.update("update game_admin_audits set details='changed'"));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from game_identity_audits"));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from games where id=?", GAME));
        assertEquals(sourceDigests, digests(target));
        MigrationSchemaSupport.validateHibernate(target);
        assertEquals(0, flyway(target, CURRENT).migrate().migrationsExecuted);
        // Yalnız satır sayıları/hash'ler; özel kayıt içerikleri loglanmaz.
        Path evidence = Path.of("../../../logs/migration-transfer-digests.txt").normalize();
        Files.createDirectories(evidence.getParent());
        Files.write(evidence, sourceDigests.entrySet().stream()
                .map(e -> e.getKey() + " rows=" + e.getValue().rows() + " sha256=" + e.getValue().sha256()).toList());
    }

    @Test void interruptionRollsBackEverythingIncludingSeedUpdate() throws Exception {
        assertRejected(SQLException.class, (connection, count) -> {
            if (count == 11) throw new SQLException("Synthetic interruption", "57014");
        });
    }

    @Test void duplicatePrimaryKeyIsRejectedRatherThanIgnored() throws Exception {
        assertRejected(SQLException.class, (connection, count) -> {
            if (count == 1) try (var statement = connection.createStatement()) {
                statement.executeUpdate("insert into games select * from games");
            }
        });
    }

    @Test void invalidForeignKeyRollsBackTransfer() throws Exception {
        assertRejected(SQLException.class, (connection, count) -> {
            if (count == 1) try (var statement = connection.createStatement()) {
                statement.executeUpdate("insert into game_languages values ('ffffffff-ffff-ffff-ffff-ffffffffffff','tr')");
            }
        });
    }

    @Test void missingRowIsDetectedBeforeCommit() throws Exception {
        assertRejected(IllegalStateException.class, (connection, count) -> {
            if (count == TABLES.size()) try (var statement = connection.createStatement()) {
                statement.executeUpdate("delete from game_developer_feedback where status='RESOLVED'");
            }
        });
    }

    @Test void sameCountWithChangedContentIsDetectedBeforeCommit() throws Exception {
        assertRejected(IllegalStateException.class, (connection, count) -> {
            if (count == TABLES.size()) try (var statement = connection.createStatement()) {
                statement.executeUpdate("update game_developer_feedback set body='Synthetic corruption'");
            }
        });
    }

    @Test void schemaMismatchPreventsCopyAndReadiness() throws Exception {
        var source = source();
        var target = database(POSTGRES, CURRENT);
        new JdbcTemplate(target).execute("create index rehearsal_unexpected on games(title)");
        var before = digests(target);
        assertThrows(IllegalStateException.class, () -> transfer(source, target, (connection, count) -> { }));
        assertEquals(before, digests(target));
        assertThrows(IllegalStateException.class, () -> requireReady(source, target, null));
    }

    @Test void postCommitHashMismatchRevokesReadiness() throws Exception {
        var source = source();
        var target = database(POSTGRES, CURRENT);
        Receipt receipt = transfer(source, target, (connection, count) -> { });
        requireReady(source, target, receipt);
        new JdbcTemplate(target).update("update games set title='Synthetic corruption' where id=?", GAME);
        assertNotEquals(receipt.tables(), digests(target));
        var failure = assertThrows(IllegalStateException.class, () -> requireReady(source, target, receipt));
        assertEquals("HASH_MISMATCH", failure.getMessage());
        assertEquals("Synthetic Atlas", new JdbcTemplate(source).queryForObject("select title from games where id=?", String.class, GAME));
    }

    private DriverManagerDataSource source() {
        var source = database(POSTGRES, HISTORY);
        populate(source);
        return source;
    }

    private List<String> history(DriverManagerDataSource source) {
        return new JdbcTemplate(source).queryForList("select row_to_json(t)::text from "
                + "(select * from flyway_schema_history order by installed_rank) t", String.class);
    }

    private void assertRejected(Class<? extends Exception> expected, Checkpoint checkpoint) throws Exception {
        var source = source();
        var target = database(POSTGRES, CURRENT);
        var beforeTarget = digests(target);
        var beforeSource = digests(source);
        var beforeHistory = history(target);
        AtomicReference<Receipt> receipt = new AtomicReference<>();
        assertThrows(expected, () -> receipt.set(transfer(source, target, checkpoint)));
        assertNull(receipt.get());
        Receipt failedReceipt = receipt.get();
        assertThrows(IllegalStateException.class, () -> requireReady(source, target, failedReceipt));
        assertEquals(beforeTarget, digests(target));
        assertEquals(beforeSource, digests(source));
        assertEquals(beforeHistory, history(target));
    }
}
