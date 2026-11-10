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

/** V175 (dile göre açıklama): dolu V174 veritabanını kayıpsız yükseltir; tablo yalnız yeni yapıdır, mevcut açıklamalara dokunmaz. */
class GameLocalizedDescriptionMigrationTest {
    @Test void populatedV174UpgradesWithoutTouchingExistingDescriptionsAndTheTableEnforcesItsRules() {
        UUID missing = UUID.randomUUID();
        try (var pg = new PostgreSQLContainer("postgres:17-alpine")) {
            pg.start();
            var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("174").load().migrate();
            var jdbc = new JdbcTemplate(ds);
            UUID game = UUID.randomUUID();
            jdbc.update("insert into games(id,title,normalized_title,origin,game_type,description,metadata_language,created_at,updated_at) values (?,'Existing','existing','EXTERNAL','MAIN_GAME','English description','en',now(),now())", game);
            jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM','10')", UUID.randomUUID(), game);
            String rows = "select to_jsonb(t)::text from games t";
            var before = jdbc.queryForList(rows, String.class);
            assertNull(jdbc.queryForList("select to_regclass('public.game_localized_descriptions')::text", String.class).getFirst());

            assertEquals(1, Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted, "yalnız V175");

            assertEquals(before, jdbc.queryForList(rows, String.class), "mevcut oyun satırları ve açıklamalar değişmez");
            assertEquals(0L, jdbc.queryForObject("select count(*) from game_localized_descriptions", Long.class), "tablo boş başlar; çeviri üretilmez");
            insert(jdbc, game, "tr", "Türkçe metin için açıklama.", "STEAM");
            insert(jdbc, game, "pt-br", "Descrição.", "STEAM");
            assertThrows(DataAccessException.class, () -> insert(jdbc, game, "tr", "Yinelenen.", "STEAM"));
            assertThrows(DataAccessException.class, () -> insert(jdbc, game, "Turkish", "x", "STEAM"));
            assertThrows(DataAccessException.class, () -> insert(jdbc, game, "de", "   ", "STEAM"));
            assertThrows(DataAccessException.class, () -> insert(jdbc, game, "de", "Text", ""));
            assertThrows(DataAccessException.class, () -> insert(jdbc, missing, "de", "Text", "STEAM"), "olmayan oyun");
            assertEquals("English description", jdbc.queryForObject("select description from games where id=?", String.class, game));
            assertEquals(0, Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted, "ikinci çalıştırma işlem yapmaz");
        }
    }

    private static void insert(JdbcTemplate jdbc, UUID game, String language, String description, String source) {
        jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,?,?,?,now())",
                UUID.randomUUID(), game, language, description, source);
    }
}
