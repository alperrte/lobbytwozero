package com.ltz.game.infrastructure.persistence;

import java.time.LocalDate;
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

/** V176 (pazar talebi sayacı): dolu V175 veritabanını kayıpsız yükseltir; tablo yalnız yeni yapıdır ve kısıtlarını uygular. */
class PriceMarketDemandMigrationTest {
    @Test void populatedV175UpgradesWithoutTouchingExistingDataAndTheTableEnforcesItsRules() {
        try (var pg = new PostgreSQLContainer("postgres:17-alpine")) {
            pg.start();
            var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("175").load().migrate();
            var jdbc = new JdbcTemplate(ds);
            UUID game = UUID.randomUUID();
            jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,'Existing','existing','EXTERNAL','MAIN_GAME',now(),now())", game);
            String rows = "select to_jsonb(t)::text from games t";
            var before = jdbc.queryForList(rows, String.class);
            assertNull(jdbc.queryForList("select to_regclass('public.game_price_market_demand')::text", String.class).getFirst());

            assertEquals(1, Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted, "yalnız V176");

            assertEquals(before, jdbc.queryForList(rows, String.class), "mevcut oyun satırları değişmez");
            assertEquals(0L, jdbc.queryForObject("select count(*) from game_price_market_demand", Long.class), "sayaç boş başlar");
            LocalDate day = LocalDate.of(2026, 11, 12);
            insert(jdbc, "US", "USD", day, 3);
            insert(jdbc, "US", "USD", day.minusDays(1), 5);
            assertThrows(DataAccessException.class, () -> insert(jdbc, "US", "USD", day, 1), "pazar ve gün tekildir");
            assertThrows(DataAccessException.class, () -> insert(jdbc, "us", "USD", day, 1), "ülke büyük harf olmalı");
            assertThrows(DataAccessException.class, () -> insert(jdbc, "US", "usd", day, 1), "para birimi büyük harf olmalı");
            assertThrows(DataAccessException.class, () -> insert(jdbc, "USA", "USD", day, 1), "ülke iki harf");
            assertThrows(DataAccessException.class, () -> insert(jdbc, "DE", "EUR", day, 0), "okuma sayısı pozitif");
            assertEquals(0, Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted, "ikinci çalıştırma işlem yapmaz");
        }
    }

    private static void insert(JdbcTemplate jdbc, String country, String currency, LocalDate day, long reads) {
        jdbc.update("insert into game_price_market_demand(id,country,currency,demand_date,read_count) values (?,?,?,?,?)",
                UUID.randomUUID(), country, currency, java.sql.Date.valueOf(day), reads);
    }
}
