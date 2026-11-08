package com.ltz.game.infrastructure.persistence;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V175 (dile göre açıklama): dolu V174 veritabanını kayıpsız yükseltir; tablo yalnız yeni yapıdır, mevcut açıklamalara dokunmaz. */
class GameLocalizedDescriptionMigrationTest {
    @Test void populatedV174UpgradesWithoutTouchingExistingDescriptionsAndTheTableEnforcesItsRules() {
        try (var pg = new PostgreSQLContainer("postgres:17-alpine")) {
            pg.start();
            var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("174").load().migrate();
            var jdbc = new JdbcTemplate(ds);
            UUID game = UUID.randomUUID();
            jdbc.update("insert into games(id,title,normalized_title,origin,game_type,description,metadata_language,created_at,updated_at) values (?,'Existing','existing','EXTERNAL','MAIN_GAME','English description','en',now(),now())", game);
            jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM','10')", UUID.randomUUID(), game);
            var before = jdbc.queryForList("select row_to_json(t)::text from games t", String.class);
            assertTrue(jdbc.queryForList("select to_regclass('public.game_localized_descriptions')::text", String.class).getFirst() == null);

            assertEquals(1, Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted);

            assertEquals(before, jdbc.queryForList("select row_to_json(t)::text from games t", String.class), "mevcut oyun satırları ve açıklamalar değişmez");
            assertEquals(0L, jdbc.queryForObject("select count(*) from game_localized_descriptions", Long.class), "tablo boş başlar; çeviri üretilmez");
            jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'tr','Türkçe metin için açıklama.','STEAM',now())", UUID.randomUUID(), game);
            jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'pt-br','Descrição.','STEAM',now())", UUID.randomUUID(), game);
            assertThrows(DataAccessException.class, () -> jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'tr','Yinelenen.','STEAM',now())", UUID.randomUUID(), game));
            assertThrows(DataAccessException.class, () -> jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'Turkish','x','STEAM',now())", UUID.randomUUID(), game));
            assertThrows(DataAccessException.class, () -> jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'de','   ','STEAM',now())", UUID.randomUUID(), game));
            assertThrows(DataAccessException.class, () -> jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'de','Text','',now())", UUID.randomUUID(), game));
            assertThrows(DataAccessException.class, () -> jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'de','Text','STEAM',now())", UUID.randomUUID(), UUID.randomUUID()), "olmayan oyun");
            assertEquals("English description", jdbc.queryForObject("select description from games where id=?", String.class, game));
            assertEquals(0, Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted, "ikinci çalıştırma işlem yapmaz");
        }
    }
}
