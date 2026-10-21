package com.ltz.game.infrastructure.persistence;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataAccessException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
class PhaseSixMigrationTest {
    @Test void populatedV171UpgradesWithoutInventingContentAndFeedbackReferencesAreRestricted() {
        try(var pg=new PostgreSQLContainer("postgres:17-alpine")) {
            pg.start(); var ds=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
            Flyway.configure().dataSource(ds).target("171").load().migrate();
            var jdbc=new JdbcTemplate(ds); UUID game=UUID.randomUUID();
            jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,metadata_language) values (?,'Existing game','existing game',now(),now(),'tr')",game);
            jdbc.update("insert into game_languages(game_id,language_code) values (?,'tr')",game);
            jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'TITLE','ADMIN','AVAILABLE',now())",game);
            Flyway.configure().dataSource(ds).load().migrate();
            assertEquals("Existing game",jdbc.queryForObject("select title from games where id=?",String.class,game));
            assertEquals("tr",jdbc.queryForObject("select metadata_language from games where id=?",String.class,game));
            assertEquals("tr",jdbc.queryForObject("select language_code from game_languages where game_id=?",String.class,game));
            assertEquals("ADMIN",jdbc.queryForObject("select source from game_metadata_provenance where game_id=?",String.class,game));
            assertEquals(0,jdbc.queryForObject("select count(*) from game_achievement_definitions",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from game_developer_feedback",Integer.class));
            assertEquals(2,jdbc.queryForObject("select count(*) from flyway_schema_history where version in ('172','173') and success",Integer.class));
            jdbc.update("insert into game_developer_feedback(id,game_id,author_id,type,title,body,created_at,updated_at) values (?,?,?,'GENERAL','Title','Body',now(),now())",UUID.randomUUID(),game,UUID.randomUUID());
            assertThrows(DataAccessException.class,()->jdbc.update("delete from games where id=?",game));
        }
    }
}
