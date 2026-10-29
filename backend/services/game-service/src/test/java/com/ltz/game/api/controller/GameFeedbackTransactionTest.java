package com.ltz.game.api.controller;
import com.ltz.game.AuthenticatedApiTest;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.AdminAuditService;
import com.ltz.game.application.service.DeveloperGrantService;
import com.ltz.game.application.service.GameFeedbackService;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.AdminAuditAction;
import com.ltz.game.domain.enums.FeedbackStatus;
import com.ltz.game.domain.enums.FeedbackType;
import com.ltz.game.domain.exception.AccessForbiddenException;
import com.ltz.game.domain.exception.GameConflictException;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
@Transactional(propagation=Propagation.NEVER)
class GameFeedbackTransactionTest extends AuthenticatedApiTest {
    @Autowired GameFeedbackService feedback;
    @Autowired DeveloperGrantService grants;
    @Autowired GameRepository games;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AdminAuditService audit;
    private Actor administrator() { return Actor.of(ADMIN_ID,Set.of(ActorRole.ADMIN)); }
    private Actor owner() { return Actor.of(DEVELOPER_ID,Set.of(ActorRole.GAME_DEVELOPER)); }
    private UUID active() {
        UUID game=game("Feedback transaction","INTERNAL","MAIN_GAME","ACTIVE");
        grants.grant(administrator(),game,DEVELOPER_ID,"Verified developer grant");
        return game;
    }
    private UUID submit(UUID game) {
        return feedback.create(Actor.of(UUID.randomUUID(),Set.of()),game,FeedbackType.GENERAL,"Title","Private feedback body").id();
    }
    @AfterEach void cleanup() {
        jdbc.update("delete from game_developer_feedback");
        jdbc.execute("truncate game_admin_audits");
        jdbc.update("delete from games");
    }
    @Test void twoConcurrentUpdatesWithSameVersionCommitExactlyOneTransition() throws Exception {
        UUID game=active(),id=submit(game);
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> update=()->{
                if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Test başlangıcı beklenemedi.");
                try { feedback.transition(owner(),game,id,FeedbackStatus.IN_PROGRESS,0L); return true; }
                catch(GameConflictException conflict) { assertEquals("VERSION_CONFLICT",conflict.code()); return false; }
            };
            var first=pool.submit(update); var second=pool.submit(update); start.countDown();
            assertNotEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
        }
        assertEquals("IN_PROGRESS",jdbc.queryForObject("select status from game_developer_feedback where id=?",String.class,id));
        assertEquals(1L,jdbc.queryForObject("select version from game_developer_feedback where id=?",Long.class,id));
        assertEquals(1,jdbc.queryForObject("select count(*) from game_admin_audits where details like ?",Integer.class,"feedback="+id+";%"));
    }
    @Test void auditFailureRollsBackAlreadyFlushedStatusAndVersion() {
        UUID game=active(),id=submit(game);
        AdminAuditService auditTarget=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(auditTarget).append(any(),eq(ActorRole.GAME_DEVELOPER),
                eq(AdminAuditAction.UPDATE_GAME),eq(game),isNull(),anyString());
        Actor developer=owner();
        assertThrows(IllegalStateException.class,()->feedback.transition(developer,game,id,FeedbackStatus.IN_PROGRESS,0L));
        assertEquals("OPEN",jdbc.queryForObject("select status from game_developer_feedback where id=?",String.class,id));
        assertEquals(0L,jdbc.queryForObject("select version from game_developer_feedback where id=?",Long.class,id));
        assertEquals(0,jdbc.queryForObject("select count(*) from game_admin_audits where details like ?",Integer.class,"feedback="+id+";%"));
    }
    @Test void feedbackCreationParticipatesInCallerTransactionRollback() {
        UUID game=active(); var tx=new TransactionTemplate(transactions);
        UUID id=tx.execute(status->{ UUID created=submit(game); status.setRollbackOnly(); return created; });
        assertEquals(0,jdbc.queryForObject("select count(*) from game_developer_feedback where id=?",Integer.class,id));
    }
    @Test void revocationCommittedWhileWriterWaitsPreventsStatusChange() throws Exception {
        UUID game=active(),id=submit(game); var tx=new TransactionTemplate(transactions);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var waiting=tx.execute(status->{
                games.lockGame(game).orElseThrow();
                Future<Boolean> writer=pool.submit(()->{
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(s->{
                            jdbc.execute("set local application_name='ltz-feedback-writer'");
                            feedback.transition(owner(),game,id,FeedbackStatus.IN_PROGRESS,0L);
                        });
                        return false;
                    } catch(AccessForbiddenException _) { return true; }
                });
                await().atMost(Duration.ofSeconds(10)).until(()->{
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where application_name='ltz-feedback-writer' and wait_event_type='Lock')",Boolean.class));
                });
                grants.revoke(administrator(),game,DEVELOPER_ID,"Verified concurrent revocation");
                return writer;
            });
            assertNotNull(waiting); assertTrue(waiting.get(15,TimeUnit.SECONDS));
        }
        assertEquals("OPEN",jdbc.queryForObject("select status from game_developer_feedback where id=?",String.class,id));
    }
}
