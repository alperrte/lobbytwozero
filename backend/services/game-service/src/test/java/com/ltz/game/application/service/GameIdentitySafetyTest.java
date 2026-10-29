package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.domain.exception.GameConflictException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bağımsız transaction'lar: kilit ve rollback gerçek PostgreSQL üzerinde sınanır. */
@SpringBootTest
class GameIdentitySafetyTest extends PostgresTestSupport {
    @Autowired GameIdentityService identity;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired jakarta.persistence.EntityManager entityManager;
    private final GameIdentityService.Verification proof=new GameIdentityService.Verification(UUID.randomUUID(),"Verified test evidence");

    @Test void deniedSplitRollsBackCallerTransaction() {
        UUID source=UUID.randomUUID(), reference=UUID.randomUUID();
        var tx=new TransactionTemplate(transactions);
        GameConflictException error=assertThrows(GameConflictException.class,()->tx.execute(status->{
            insert(source,"Rollback source");
            jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM','4123456788')",reference,source);
            return identity.safeSplit("STEAM","4123456788",source,proof);
        }));
        assertEquals("FOREIGN_REFERENCE_SAFETY_UNVERIFIED",error.code());
        assertEquals(0L,jdbc.queryForObject("select count(*) from games where id=?",Long.class,source));
        assertEquals(0L,jdbc.queryForObject("select count(*) from game_external_refs where id=?",Long.class,reference));
        assertEquals(0L,jdbc.queryForObject("select count(*) from game_identity_audits where reference_id=?",Long.class,reference));
    }

    @Test void safeNoOpWaitsForConcurrentMappingRollbackAndSeesOriginalIdentity() throws Exception {
        UUID source=UUID.randomUUID(), target=UUID.randomUUID(), reference=UUID.randomUUID();
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{
            insert(source,"Concurrent source"); insert(target,"Concurrent target");
            jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM','4123456789')",reference,source);
        });
        CountDownLatch mapped=new CountDownLatch(1), release=new CountDownLatch(1), readerStarted=new CountDownLatch(1);
        try (ExecutorService workers=Executors.newFixedThreadPool(2)) {
            try {
                Future<?> writer=workers.submit(()->tx.executeWithoutResult(status->{
                    identity.reassign("STEAM","4123456789",source,target,proof);
                    // Flush ile eşleme ve audit gerçekten DB'ye gönderilir, sonra transaction geri alınır.
                    entityManager.flush();
                    mapped.countDown();
                    await(release);
                    status.setRollbackOnly();
                }));
                assertTrue(mapped.await(10,TimeUnit.SECONDS));
                Future<UUID> reader=workers.submit(()->{
                    readerStarted.countDown();
                    return identity.safeReassign("STEAM","4123456789",source,source,proof);
                });
                assertTrue(readerStarted.await(10,TimeUnit.SECONDS));
                assertThrows(TimeoutException.class,()->reader.get(250,TimeUnit.MILLISECONDS));
                release.countDown();
                writer.get(10,TimeUnit.SECONDS);
                assertEquals(source,reader.get(10,TimeUnit.SECONDS));
                assertEquals(source,jdbc.queryForObject("select game_id from game_external_refs where id=?",UUID.class,reference));
                assertEquals(0L,jdbc.queryForObject("select count(*) from game_identity_audits where reference_id=?",Long.class,reference));
            } finally {
                release.countDown(); workers.shutdown();
                if (!workers.awaitTermination(15,TimeUnit.SECONDS)) workers.shutdownNow();
                tx.executeWithoutResult(status->{
                    jdbc.update("delete from game_external_refs where id=?",reference);
                    jdbc.update("delete from games where id in (?,?)",source,target);
                });
            }
        }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Test kilit bekleme süresi aşıldı."); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
    private void insert(UUID id,String title) {
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at) values (?,?,?,now(),now())",id,title,title.toLowerCase(java.util.Locale.ROOT));
    }
}
