package com.ltz.game.infrastructure.persistence;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V171 verisi üzerinde V172 (LTZ puan projeksiyonu) yükseltmesi ve tablo kısıtları. */
class RatingProjectionMigrationTest {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    JdbcTemplate jdbc;
    DriverManagerDataSource source;
    UUID game = UUID.randomUUID();

    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @BeforeEach void database() {
        String db = "rating_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
                .execute("create database " + db);
        source = new DriverManagerDataSource("jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + db,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("171").load().migrate();
        jdbc = new JdbcTemplate(source);
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at) values (?,'Preserved','preserved',now(),now())", game);
        assertEquals(1, Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate().migrationsExecuted);
    }

    @Test void upgradePreservesDataSeedsTheSingleStateRowAndValidates() {
        assertEquals("172", jdbc.queryForObject("select version from flyway_schema_history order by installed_rank desc limit 1", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from games", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from game_rating_sync_state", Integer.class));
        assertNull(jdbc.queryForObject("select last_completed_at from game_rating_sync_state", Object.class), "ilk tarama bitmeden hazır sayılmaz");
        assertEquals(0L, jdbc.queryForObject("select scan_generation from game_rating_sync_state", Long.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from game_rating_projection", Integer.class));
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().validate();
        assertEquals(0, Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate().migrationsExecuted);
    }

    @Test void stateTableIsASingletonWithAConsistentLease() {
        assertThrows(DataAccessException.class, () -> jdbc.update("insert into game_rating_sync_state(id) values (?)", UUID.randomUUID()));
        assertThrows(DataAccessException.class, () -> jdbc.update("update game_rating_sync_state set lease_owner = ?", UUID.randomUUID()));
        assertThrows(DataAccessException.class, () -> jdbc.update("update game_rating_sync_state set lease_until = now()"));
        assertThrows(DataAccessException.class, () -> jdbc.update("update game_rating_sync_state set scan_generation = -1"));
    }

    @Test void projectionRowsAreConsistentBoundedAndCascadeWithTheGame() {
        String insert = "insert into game_rating_projection(game_id,average_rating,rating_count,last_seen_scan,synced_at) values (?,?,?,?,now())";
        jdbc.update(insert, game, new java.math.BigDecimal("10.00"), 4L, 1L);
        assertThrows(DataAccessException.class, () -> jdbc.update(insert, game, new java.math.BigDecimal("5.00"), 1L, 1L), "tekrarlı oyun");
        assertThrows(DataAccessException.class, () -> jdbc.update(insert, UUID.randomUUID(), new java.math.BigDecimal("5.00"), 1L, 1L), "olmayan oyun");
        UUID other = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at) values (?,'Other','other',now(),now())", other);
        assertThrows(DataAccessException.class, () -> jdbc.update(insert, other, new java.math.BigDecimal("0.99"), 1L, 1L));
        assertThrows(DataAccessException.class, () -> jdbc.update(insert, other, new java.math.BigDecimal("10.01"), 1L, 1L));
        assertThrows(DataAccessException.class, () -> jdbc.update(insert, other, new java.math.BigDecimal("5.00"), 0L, 1L), "puanlı ama sayı 0");
        assertThrows(DataAccessException.class, () -> jdbc.update(insert, other, null, 2L, 1L), "sayı var ama ortalama yok");
        assertThrows(DataAccessException.class, () -> jdbc.update(insert, other, new java.math.BigDecimal("5.00"), 1L, 0L), "tarama sayacı pozitif");
        jdbc.update(insert, other, null, 0L, 1L);
        assertNotNull(jdbc.queryForObject("select synced_at from game_rating_projection where game_id=?", Object.class, other));
        jdbc.update("delete from games where id=?", game);
        assertEquals(1, jdbc.queryForObject("select count(*) from game_rating_projection", Integer.class));
    }
}
