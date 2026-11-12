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

/**
 * Steam durum kolonları (V31 games tablo tanımı: free, coming_soon, adult, delisted_at) ve fiyat kaynağı kısıtı (V173: ITAD veya STEAM). Bu alanlar önce V176 ile
 * eklenmişti; kullanıcı onaylı tek seferlik kararla ilgili tablo tanımlarına taşındı. Yeni kolonlar boş (bilinmiyor) başlar, kaynak kısıtı yalnız ITAD ve STEAM'i
 * kabul eder, zincir uçtan uca temiz uygulanır.
 */
class GameReleaseStatusMigrationTest {
    @Test void freshChainHasTheSteamStatusColumnsStartingUnknownAndThePriceSourceAcceptsOnlyItadAndSteam() {
        try (var pg = new PostgreSQLContainer("postgres:17-alpine")) {
            pg.start();
            var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            assertEquals(16, Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted);
            var jdbc = new JdbcTemplate(ds);
            UUID game = UUID.randomUUID();
            jdbc.update("insert into games(id,title,normalized_title,origin,game_type,early_access,created_at,updated_at) values (?,'Existing','existing','EXTERNAL','MAIN_GAME',true,now(),now())", game);
            for (String column : new String[]{"free", "coming_soon", "adult"})
                assertNull(jdbc.queryForObject("select " + column + " from games where id=?", Boolean.class, game), column + " boş (bilinmiyor) başlar");
            assertNull(jdbc.queryForObject("select delisted_at from games where id=?", java.sql.Timestamp.class, game));
            jdbc.update("update games set free=true, coming_soon=false, adult=true, delisted_at=now() where id=?", game);
            assertEquals(Boolean.TRUE, jdbc.queryForObject("select adult from games where id=?", Boolean.class, game));

            quote(jdbc, game, "GOG", "ITAD");
            quote(jdbc, game, "Steam", "STEAM");
            assertThrows(DataAccessException.class, () -> quote(jdbc, game, "Other", "OTHER"), "tanımsız kaynak reddedilir");
            assertThrows(DataAccessException.class, () -> quote(jdbc, game, "Steam", "STEAM"), "aynı oyun/pazar/mağaza tekildir");
            assertEquals(0, Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate().migrationsExecuted, "ikinci çalıştırma işlem yapmaz");
        }
    }

    private static void quote(JdbcTemplate jdbc, UUID game, String store, String source) {
        jdbc.update("insert into game_price_quotes(id,game_id,country,currency,store,regular_price,current_price,discount_percent,source,url,source_updated_at,fetched_at,expires_at)"
                + " values (?,?,'US','USD',?,10,5,50,?,'https://store.example/app/10',now(),now(),now() + interval '1 hour')", UUID.randomUUID(), game, store, source);
    }
}
