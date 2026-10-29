package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.DeveloperContentService;
import com.ltz.game.application.service.DeveloperGrantService;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.exception.AccessForbiddenException;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gerçek PostgreSQL satır kilidi ve commit sırasıyla yetki iptali yarışı. */
@Transactional(propagation = Propagation.NEVER)
class DeveloperRevocationRaceTest extends AuthenticatedApiTest {
    private static final String REASON = "Yetki iptali yarış testi gerekçesi";
    @Autowired GameRepository games;
    @Autowired DeveloperGrantService grants;
    @Autowired DeveloperContentService contents;
    @Autowired PlatformTransactionManager manager;

    private Actor owner() { return Actor.of(DEVELOPER_ID, Set.of(ActorRole.GAME_DEVELOPER)); }
    private Actor administrator() { return Actor.of(ADMIN_ID, Set.of(ActorRole.ADMIN)); }

    private TransactionTemplate transaction() {
        var tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }

    private UUID grantedGame() {
        UUID id = game("Revocation race", "INTERNAL", "MAIN_GAME", "ACTIVE");
        grants.grant(administrator(), id, DEVELOPER_ID, REASON);
        return id;
    }

    @AfterEach void cleanup() {
        jdbc.execute("truncate game_admin_audits");
        jdbc.update("delete from games");
    }

    @Test void committedRevocationRejectsWriterAlreadyWaitingOnTheGameLock() throws Exception {
        UUID id = grantedGame();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = transaction().execute(status -> {
                games.lockGame(id).orElseThrow();
                var result = executor.submit(() -> {
                    try {
                        transaction().executeWithoutResult(s -> {
                            jdbc.execute("set local application_name='ltz-revocation-writer'");
                            contents.putDescription(owner(), id, "İptal sonrası yazılmamalı", null);
                        });
                        return false;
                    } catch (AccessForbiddenException _) {
                        return true;
                    }
                });
                awaitBlockedWriter();
                grants.revoke(administrator(), id, DEVELOPER_ID, REASON);
                return result;
            });
            assertNotNull(writer);
            assertTrue(writer.get(20, TimeUnit.SECONDS), "İptal commit edildikten sonra bekleyen yazma reddedilmeli");
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from game_developer_contents where game_id=?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("select count(*) from game_developer_grants where game_id=? and revoked_at is null", Integer.class, id));
    }

    private void awaitBlockedWriter() {
        await().pollInSameThread().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10))
                .atMost(Duration.ofSeconds(15)).until(() -> {
            // pg_stat_activity aynı observer transaction'ında snapshot önbellekler.
            jdbc.execute("select pg_stat_clear_snapshot()");
            Boolean waiting = jdbc.queryForObject("select exists(select 1 from pg_stat_activity where application_name='ltz-revocation-writer' and wait_event_type='Lock')", Boolean.class);
            return Boolean.TRUE.equals(waiting);
        });
    }

    @Test void writerHoldingGameLockCommitsBeforeRevocationAndLaterWritesAreRejected() throws Exception {
        UUID id = grantedGame();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var revocation = transaction().execute(status -> {
                contents.putDescription(owner(), id, "İptalden önce geçerli yazma", null);
                var result = executor.submit(() -> {
                    transaction().executeWithoutResult(s -> {
                        jdbc.execute("set local application_name='ltz-revocation-writer'");
                        grants.revoke(administrator(), id, DEVELOPER_ID, REASON);
                    });
                });
                awaitBlockedWriter();
                return result;
            });
            assertNotNull(revocation);
            revocation.get(20, TimeUnit.SECONDS);
        }
        assertEquals("İptalden önce geçerli yazma", jdbc.queryForObject("select description from game_developer_contents where game_id=?", String.class, id));
        Actor actor = owner();
        assertThrows(AccessForbiddenException.class, () -> contents.putDescription(actor, id, "Reddedilmeli", 0L));
    }
}
