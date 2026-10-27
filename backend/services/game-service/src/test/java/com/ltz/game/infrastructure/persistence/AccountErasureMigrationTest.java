package com.ltz.game.infrastructure.persistence;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Mevcut V40 verisinden yükseltme, gerçek veri temizliği ve DB yetki sınırı. */
class AccountErasureMigrationTest {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static final UUID NIL = new UUID(0, 0);
    JdbcTemplate jdbc;
    DriverManagerDataSource source;
    UUID account = UUID.randomUUID(), other = UUID.randomUUID(), game = UUID.randomUUID();

    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }
    @BeforeEach void database() {
        String db = "erase_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("create database " + db);
        source = new DriverManagerDataSource("jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + db,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("40").load().migrate();
        jdbc = new JdbcTemplate(source);
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,editor_actor_id,editor_evidence) values (?,'Preserved game','preserved game',now(),now(),?,'private editorial evidence')", game, account);
        jdbc.update("insert into game_admin_audits(id,game_id,actor_id,actor_role,action,reason,details,created_at) values (?,?,?,'ADMIN','UPDATE_GAME','private audit reason','private details',now())", UUID.randomUUID(), game, account);
        assertEquals(1, Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate().migrationsExecuted);
    }

    void erase() { jdbc.queryForObject("select public.erase_game_account_data(?)", String.class, account); }

    @Test void migrationPreservesExistingDataUntilExplicitErasureAndValidates() {
        assertEquals(account, jdbc.queryForObject("select editor_actor_id from games where id=?", UUID.class, game));
        assertEquals(account, jdbc.queryForObject("select actor_id from game_admin_audits", UUID.class));
        assertEquals("171", jdbc.queryForObject("select version from flyway_schema_history order by installed_rank desc limit 1", String.class));
        assertEquals(0, count("game_account_erasure_context"));
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().validate();
    }

    @Test void erasureDeletesOwnedRowsButPreservesCatalogOtherUsersAndRevocation() {
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at) values (?,?,'TITLE',?,now())", UUID.randomUUID(), game, account);
        UUID ownGrant = UUID.randomUUID(), otherGrant = UUID.randomUUID();
        jdbc.update("insert into game_developer_grants(id,game_id,developer_user_id,granted_by,granted_at) values (?,?,?,?,now())", ownGrant, game, account, other);
        jdbc.update("insert into game_developer_grants(id,game_id,developer_user_id,granted_by,granted_at,revoked_by,revoked_at) values (?,?,?,?,now(),?,now())", otherGrant, game, other, account, account);
        content(account, game);
        UUID otherGame = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,editor_actor_id) values (?,'Other','other',now(),now(),?)", otherGame, other);
        content(other, otherGame);
        for (String table : new String[]{"game_developer_contents", "game_developer_media", "game_update_notes"})
            jdbc.update("update " + table + " set moderated_by=?, moderation_reason='private moderator reason' where author_id=?", account, other);
        erase();
        erase();
        assertEquals(2, count("games"));
        assertNull(jdbc.queryForObject("select editor_actor_id from games where id=?", UUID.class, game));
        assertNull(jdbc.queryForObject("select editor_evidence from games where id=?", String.class, game));
        assertEquals(other, jdbc.queryForObject("select editor_actor_id from games where id=?", UUID.class, otherGame));
        for (String table : new String[]{"game_developer_contents", "game_developer_media", "game_update_notes", "game_developer_feedback"}) {
            assertEquals(0, jdbc.queryForObject("select count(*) from " + table + " where author_id=?", Integer.class, account));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + table + " where author_id=?", Integer.class, other));
        }
        assertEquals(1, count("game_developer_grants"));
        assertEquals(NIL, jdbc.queryForObject("select granted_by from game_developer_grants where id=?", UUID.class, otherGrant));
        assertEquals(NIL, jdbc.queryForObject("select revoked_by from game_developer_grants where id=?", UUID.class, otherGrant));
        assertNotNull(jdbc.queryForObject("select revoked_at from game_developer_grants where id=?", Object.class, otherGrant));
        assertEquals(NIL, jdbc.queryForObject("select actor_id from game_field_overrides", UUID.class));
        assertEquals(0, count("game_account_erasure_context"));
        for (String table : new String[]{"game_developer_contents", "game_developer_media", "game_update_notes"}) {
            assertNull(jdbc.queryForObject("select moderated_by from " + table + " where author_id=?", UUID.class, other));
            assertNull(jdbc.queryForObject("select moderation_reason from " + table + " where author_id=?", String.class, other));
        }
    }

    @Test void identityAuditAndTargetPersonAuditAreAnonymizedWithoutLosingOtherActor() {
        UUID second = UUID.randomUUID(), reference = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at) values (?,'Second','second',now(),now())", second);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM','123456')", reference, game);
        jdbc.update("insert into game_identity_audits(id,reference_id,from_game_id,to_game_id,actor_id,action,evidence,created_at) values (?,?,?,?,?,'MAP','private identity evidence',now())", UUID.randomUUID(), reference, game, second, account);
        UUID targetAudit = UUID.randomUUID(), unrelated = UUID.randomUUID();
        jdbc.update("insert into game_admin_audits(id,actor_id,actor_role,action,reason,details,created_at) values (?,?,'ADMIN','GRANT_DEVELOPER','target person reason',?,now())", targetAudit, other, "developerUserId=" + account);
        jdbc.update("insert into game_admin_audits(id,actor_id,actor_role,action,reason,details,created_at) values (?,?,'ADMIN','GRANT_DEVELOPER','unrelated reason',?,now())", unrelated, other, "developerUserId=" + other);
        erase();
        assertEquals(NIL, jdbc.queryForObject("select actor_id from game_identity_audits", UUID.class));
        assertEquals("[silinen hesap: metin kaldırıldı]", jdbc.queryForObject("select evidence from game_identity_audits", String.class));
        assertEquals(other, jdbc.queryForObject("select actor_id from game_admin_audits where id=?", UUID.class, targetAudit));
        assertNull(jdbc.queryForObject("select reason from game_admin_audits where id=?", String.class, targetAudit));
        assertNull(jdbc.queryForObject("select details from game_admin_audits where id=?", String.class, targetAudit));
        assertEquals("unrelated reason", jdbc.queryForObject("select reason from game_admin_audits where id=?", String.class, unrelated));
        assertThrows(DataAccessException.class, () -> jdbc.update("update game_identity_audits set evidence='arbitrary change'"));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from game_identity_audits"));
    }

    @Test void normalUpdatesDeletesAndForgedSessionFlagRemainForbidden() {
        jdbc.execute("set ltz.account_erasure_id='" + account + "'");
        assertThrows(DataAccessException.class, () -> jdbc.update("update game_admin_audits set actor_id=?,reason=null,details=null", NIL));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from game_admin_audits"));
        erase();
        assertThrows(DataAccessException.class, () -> jdbc.update("update game_admin_audits set reason='arbitrary change'"));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from game_admin_audits"));
    }

    @Test void runtimeRoleCannotForgeContextAndOnlyExplicitErasureFunctionIsGranted() {
        String role = "erasure_runtime_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create role " + role);
        jdbc.execute("grant usage on schema public to " + role);
        jdbc.execute("grant select, update, delete on game_admin_audits to " + role);
        assertDoesNotThrow(() -> {
            try (var connection = source.getConnection(); var sql = connection.createStatement()) {
                sql.execute("set role " + role);
                sql.execute("set ltz.account_erasure_id='" + account + "'");
                assertThrows(java.sql.SQLException.class, () -> sql.execute("insert into public.game_account_erasure_context(transaction_id,account_id) values (txid_current(),'" + account + "')"));
                assertThrows(java.sql.SQLException.class, () -> sql.execute("update public.game_admin_audits set reason=null,details=null"));
                assertThrows(java.sql.SQLException.class, () -> sql.execute("select public.erase_game_account_data('" + account + "')"));
                jdbc.execute("grant execute on function erase_game_account_data(uuid) to " + role);
                assertTrue(sql.execute("select public.erase_game_account_data('" + account + "')"));
            }
        });
        assertEquals(NIL, jdbc.queryForObject("select actor_id from game_admin_audits", UUID.class));
        assertEquals(0, count("game_account_erasure_context"));
    }

    @Test void midErasureFailureRollsBackEveryTableAndContext() {
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at) values (?,?,'TITLE',?,now())", UUID.randomUUID(), game, account);
        jdbc.execute("create function reject_erasure_test() returns trigger language plpgsql as $$ begin raise exception 'test rollback'; end $$");
        jdbc.execute("create trigger reject_erasure_test before update on game_field_overrides for each row execute function reject_erasure_test()");
        assertThrows(DataAccessException.class, this::erase);
        assertEquals(account, jdbc.queryForObject("select editor_actor_id from games where id=?", UUID.class, game));
        assertEquals(account, jdbc.queryForObject("select actor_id from game_admin_audits", UUID.class));
        assertEquals("private audit reason", jdbc.queryForObject("select reason from game_admin_audits", String.class));
        assertEquals(0, count("game_account_erasure_context"));
        jdbc.execute("drop trigger reject_erasure_test on game_field_overrides");
        erase();
        assertEquals(NIL, jdbc.queryForObject("select actor_id from game_admin_audits", UUID.class));
    }

    @Test void nilAccountCannotEraseTheSharedAnonymousActor() {
        assertThrows(DataAccessException.class, () -> jdbc.queryForObject("select public.erase_game_account_data(?)", String.class, NIL));
        assertEquals(account, jdbc.queryForObject("select actor_id from game_admin_audits", UUID.class));
    }

    @Test void lockContentionFailsWithoutPartialErasureAndRetrySucceeds() throws Exception {
        try (var writer = source.getConnection()) {
            writer.setAutoCommit(false);
            try (var sql = writer.prepareStatement("update games set version=version+1 where id=?")) {
                sql.setObject(1, game);
                sql.executeUpdate();
            }
            assertThrows(DataAccessException.class, this::erase);
            assertEquals(account, jdbc.queryForObject("select actor_id from game_admin_audits", UUID.class));
            assertEquals(0, count("game_account_erasure_context"));
            writer.rollback();
        }
        erase();
        assertEquals(NIL, jdbc.queryForObject("select actor_id from game_admin_audits", UUID.class));
    }

    int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    void content(UUID author, UUID targetGame) {
        jdbc.update("insert into game_developer_contents(game_id,author_id,description,status,created_at,updated_at) values (?,?,'private description','VISIBLE',now(),now())", targetGame, author);
        jdbc.update("insert into game_developer_media(id,game_id,author_id,type,url,status,created_at,updated_at) values (?,?,?,'IMAGE','https://example.invalid/image','VISIBLE',now(),now())", UUID.randomUUID(), targetGame, author);
        jdbc.update("insert into game_update_notes(id,game_id,author_id,title,body,status,created_at,updated_at) values (?,?,?,'Private title','Private body','VISIBLE',now(),now())", UUID.randomUUID(), targetGame, author);
        jdbc.update("insert into game_developer_feedback(id,game_id,author_id,type,title,body,created_at,updated_at) values (?,?,?,'GENERAL','Private title','Private body',now(),now())", UUID.randomUUID(), targetGame, author);
    }
}
