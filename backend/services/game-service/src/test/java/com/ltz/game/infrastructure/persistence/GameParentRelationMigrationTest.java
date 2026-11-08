package com.ltz.game.infrastructure.persistence;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V174 (DLC → ana oyun ilişkisi): dolu bir V173 veritabanını kayıpsız yükseltir ve bütünlük kurallarını DB düzeyinde uygular:
 * kendine bağlanma, DLC olmayan kayıt, DLC/UNKNOWN ana oyun ve olmayan ana oyun reddedilir; ana oyun silinirse veya MAIN_GAME'liğini
 * kaybederse DLC korunur, yalnız ilişki kopar.
 */
class GameParentRelationMigrationTest {
    private static UUID game(JdbcTemplate jdbc, String title, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,?,?,'EXTERNAL',?,now(),now())",
                id, title, title.toLowerCase(java.util.Locale.ROOT), type);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM',?)", UUID.randomUUID(), id, Integer.toString(Math.abs(title.hashCode())));
        return id;
    }

    @Test void populatedV173UpgradesLosslesslyAndTheRelationRulesAreEnforcedByTheDatabase() {
        try (var pg = new PostgreSQLContainer("postgres:17-alpine")) {
            pg.start();
            var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("173").load().migrate();
            var jdbc = new JdbcTemplate(ds);
            UUID main = game(jdbc, "Main Game", "MAIN_GAME"), dlc = game(jdbc, "Dlc One", "DLC"), unknown = game(jdbc, "Unknown One", "UNKNOWN");
            jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'TITLE','ADMIN','AVAILABLE',now())", dlc);
            var beforeData = jdbc.queryForList("select id, title, game_type from games order by id").toString();
            assertTrue(jdbc.queryForList("select column_name from information_schema.columns where table_name='games' and column_name='parent_game_id'", String.class).isEmpty());

            assertEquals(1, Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("174").load().migrate().migrationsExecuted);

            assertEquals(beforeData, jdbc.queryForList("select id, title, game_type from games order by id").toString(), "mevcut satırlar değişmez");
            assertEquals(0L, jdbc.queryForObject("select count(*) from games where parent_game_id is not null", Long.class));
            assertEquals(3L, jdbc.queryForObject("select count(*) from game_external_refs", Long.class), "dış referanslar korunur");
            assertEquals("ADMIN", jdbc.queryForObject("select source from game_metadata_provenance where game_id=?", String.class, dlc));

            // Geçerli bağlantı
            jdbc.update("update games set parent_game_id=? where id=?", main, dlc);
            assertEquals(main, jdbc.queryForObject("select parent_game_id from games where id=?", UUID.class, dlc));

            // Kendine bağlanma, DLC olmayan kayıt, olmayan/UNKNOWN/DLC ana oyun reddedilir
            assertThrows(DataAccessException.class, () -> jdbc.update("update games set parent_game_id=id where id=?", dlc));
            assertThrows(DataAccessException.class, () -> jdbc.update("update games set parent_game_id=? where id=?", main, unknown), "DLC olmayan kayıt bağlanamaz");
            UUID dlcTwo = game(jdbc, "Dlc Two", "DLC");
            assertThrows(DataAccessException.class, () -> jdbc.update("update games set parent_game_id=? where id=?", unknown, dlcTwo), "UNKNOWN ana oyun reddedilir");
            assertThrows(DataAccessException.class, () -> jdbc.update("update games set parent_game_id=? where id=?", dlc, dlcTwo), "DLC başka DLC'nin ana oyunu olamaz (döngü/zincir yok)");
            assertThrows(DataAccessException.class, () -> jdbc.update("update games set parent_game_id=? where id=?", UUID.randomUUID(), dlcTwo), "olmayan ana oyun");
            assertThrows(DataAccessException.class, () -> jdbc.update("update games set game_type='MAIN_GAME' where id=?", dlc), "ilişkili kayıt DLC dışına geçemez (uygulama ilişkiyi önce temizler)");
            assertNull(jdbc.queryForObject("select parent_game_id from games where id=?", UUID.class, dlcTwo));

            // Ana oyun MAIN_GAME'liğini kaybederse DLC korunur, ilişki kopar
            UUID otherMain = game(jdbc, "Other Main", "MAIN_GAME"), child = game(jdbc, "Child Dlc", "DLC");
            jdbc.update("update games set parent_game_id=? where id=?", otherMain, child);
            jdbc.update("update games set game_type='DEMO' where id=?", otherMain);
            assertNull(jdbc.queryForObject("select parent_game_id from games where id=?", UUID.class, child));
            assertEquals("DLC", jdbc.queryForObject("select game_type from games where id=?", String.class, child));

            // Ana oyun silinirse DLC silinmez, ilişki kopar (FK ON DELETE SET NULL); referanslı oyun silme kısıtı başka testlerde
            UUID third = game(jdbc, "Third Main", "MAIN_GAME"), orphan = game(jdbc, "Orphan Dlc", "DLC");
            jdbc.update("update games set parent_game_id=? where id=?", third, orphan);
            jdbc.update("delete from game_external_refs where game_id=?", third);
            jdbc.update("delete from games where id=?", third);
            assertNull(jdbc.queryForObject("select parent_game_id from games where id=?", UUID.class, orphan));
            assertEquals(1L, jdbc.queryForObject("select count(*) from games where id=?", Long.class, orphan));

            // Ana oyunun aktif DLC'leri için kısmi indeks mevcut
            assertTrue(jdbc.queryForObject("select count(*) from pg_indexes where indexname='ix_games_parent_game'", Long.class) == 1L);
        }
    }
}
