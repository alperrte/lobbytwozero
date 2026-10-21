package com.ltz.game.infrastructure.persistence;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
class LocalizationMigrationTest {
    @Test void upgradingPopulatedV40PreservesGeneralLanguagesAndDoesNotInventVerification() {
        try(var postgres=new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var dataSource=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
            Flyway.configure().dataSource(dataSource).target("40").load().migrate();
            var jdbc=new JdbcTemplate(dataSource); UUID id=UUID.randomUUID();
            jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness) values(?,'Legacy','legacy',now(),now(),'EXTERNAL','MAIN_GAME','ACTIVE','INDEX_ONLY')",id);
            jdbc.update("insert into game_languages(game_id,language_code) values(?,'tr')",id);
            Flyway.configure().dataSource(dataSource).load().migrate();
            assertEquals("Legacy",jdbc.queryForObject("select title from games where id=?",String.class,id));
            assertEquals("tr",jdbc.queryForObject("select language_code from game_languages where game_id=?",String.class,id));
            assertEquals("UNKNOWN",jdbc.queryForObject("select language_data_state from games where id=?",String.class,id));
            assertNull(jdbc.queryForObject("select metadata_language from games where id=?",String.class,id));
            assertEquals(0,jdbc.queryForObject("select count(*) from game_language_supports",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from game_metadata_provenance",Integer.class));
        }
    }
}
