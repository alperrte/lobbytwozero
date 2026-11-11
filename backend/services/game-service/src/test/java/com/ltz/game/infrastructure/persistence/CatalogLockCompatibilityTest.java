package com.ltz.game.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Katalog işlerinin oyun satırı kilidi ({@code lockCatalogGames}), fiyat yenilemesinin {@code game_price_refresh_state} yazımıyla çakışmaz.
 * Fiyat satırı {@code games}'e yabancı anahtarla bağlıdır ve yazılırken oyun satırında "anahtar paylaşım" kilidi tutar; satır kilidi
 * {@code for update} olsaydı iki taraf birbirini bekler ve veritabanı deadlock bulurdu (DEV günlüğünde görüldü). {@code for no key update} bu kilitle
 * çakışmaz.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
class CatalogLockCompatibilityTest extends PostgresTestSupport {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired GameRepository games;
    @Autowired PlatformTransactionManager transactions;

    @Test void lockingCatalogGamesDoesNotWaitForAPriceRefreshThatHoldsTheForeignKeyShareLockOnTheSameGame() throws Exception {
        jdbc.update("delete from game_price_refresh_state"); jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
        UUID game = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,'Locked','locked','EXTERNAL','MAIN_GAME',now(),now())", game);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM','990001',now())", UUID.randomUUID(), game);
        try (ExecutorService executor = Executors.newSingleThreadExecutor(); Connection price = dataSource.getConnection()) {
            price.setAutoCommit(false);
            try (var insert = price.prepareStatement("insert into game_price_refresh_state(id,game_id,country,currency,status,checked_at) values (?,?,'US','USD','AVAILABLE',?)")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, game);
                insert.setTimestamp(3, java.sql.Timestamp.from(Instant.now()));
                insert.executeUpdate();
            }
            // Fiyat işlemi açık ve oyun satırında FK paylaşım kilidi tutuyor; katalog kilidi beklememeli.
            Callable<List<UUID>> lock = () -> new TransactionTemplate(transactions).execute(status -> games.lockCatalogGames("STEAM", List.of("990001")));
            Future<List<UUID>> result = executor.submit(lock);
            List<UUID> locked = result.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(game), locked);
            assertFalse(result.isCancelled());
            price.commit();
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from game_price_refresh_state where game_id=?", Integer.class, game));
    }

    @Test void updatingAnOrdinaryGameColumnDoesNotWaitForAPriceRefreshThatHoldsTheForeignKeyShareLock() throws Exception {
        jdbc.update("delete from game_price_refresh_state"); jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
        UUID game = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,'Before','before','EXTERNAL','MAIN_GAME',now(),now())", game);
        try (ExecutorService executor = Executors.newSingleThreadExecutor(); Connection price = dataSource.getConnection()) {
            price.setAutoCommit(false);
            try (var insert = price.prepareStatement("insert into game_price_refresh_state(id,game_id,country,currency,status,checked_at) values (?,?,'US','USD','AVAILABLE',?)")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, game);
                insert.setTimestamp(3, java.sql.Timestamp.from(Instant.now()));
                insert.executeUpdate();
            }
            // Tüm sütunları yazan güncelleme editor_rank'i de SET'e koyar ve FK paylaşım kilidiyle çakışırdı (DEV günlüğündeki deadlock).
            Callable<Boolean> update = () -> new TransactionTemplate(transactions).execute(status -> {
                games.findById(game).orElseThrow().setTitle("After");
                return true;
            });
            Future<Boolean> result = executor.submit(update);
            assertEquals(true, result.get(5, TimeUnit.SECONDS));
            price.commit();
        }
        assertEquals("After", jdbc.queryForObject("select title from games where id=?", String.class, game));
    }
}
