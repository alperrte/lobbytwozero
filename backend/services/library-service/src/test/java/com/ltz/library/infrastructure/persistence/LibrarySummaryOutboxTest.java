package com.ltz.library.infrastructure.persistence;

import com.ltz.library.application.port.GameCatalogPort;
import com.ltz.library.application.port.LibrarySummaryPublisher;
import com.ltz.library.application.service.AccountErasureService;
import com.ltz.library.application.service.LibraryEntryWriter;
import com.ltz.library.application.service.LibrarySummaryOutbox;
import com.ltz.library.application.service.LibrarySummaryRelay;
import com.ltz.library.domain.enums.LibraryAction;
import com.ltz.library.domain.enums.LibraryFlag;
import com.ltz.library.domain.exception.SummaryDeliveryException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LIB-10 özet outbox'ı: gerçek PostgreSQL/Flyway üzerinde işaretleme, coalescing, kiralama, geri çekilme, silme ve V43
 * başlangıç aktarımı. User'a HTTP gönderimi {@link LibrarySummaryPublisher} sahte nesnesiyle sınanır (adaptör ayrı testte).
 */
@SpringBootTest(properties = {"logging.level.root=WARN", "ltz.library.outbox.relay-enabled=false",
        "ltz.security.jwt.jwk-set-uri=http://127.0.0.1:9/jwks"})
class LibrarySummaryOutboxTest {
    private static final String TEST_DATABASE = "library-summary-outbox-test";
    private static final String TEST_USER = "ltz_library_summary_test";
    /** CI kendi izole PostgreSQL'ini verir; yerelde geçici konteyner açılır, geliştirici DB'sine dokunulmaz. */
    private static final PostgreSQLContainer POSTGRES = Boolean.getBoolean("ltz.test.external-postgres") ? null : startContainer();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID GAME_A = UUID.randomUUID();
    private static final UUID GAME_B = UUID.randomUUID();
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LibraryEntryWriter writer;
    @Autowired private LibrarySummaryOutbox outbox;
    @Autowired private LibrarySummaryRelay relay;
    @Autowired private AccountErasureService erasure;
    @MockitoBean private LibrarySummaryPublisher publisher;
    @MockitoBean private GameCatalogPort catalog;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    @BeforeEach void clean() {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        String user = jdbc.queryForObject("SELECT current_user", String.class);
        boolean dedicated = TEST_DATABASE.equals(database) && TEST_USER.equals(user);
        boolean ci = Boolean.getBoolean("ltz.test.external-postgres") && "library-service".equals(database) && "ltz_test".equals(user);
        assertTrue(dedicated || ci, "Veri temizliği yalnız ayrılmış Library test veritabanında çalışır.");
        jdbc.update("DELETE FROM library_summary_outbox");
        jdbc.update("DELETE FROM library_entries");
        reset(publisher);
        when(publisher.ready()).thenReturn(true);
    }

    private static PostgreSQLContainer startContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName(TEST_DATABASE).withUsername(TEST_USER);
        container.start();
        return container;
    }

    private long markers() { return jdbc.queryForObject("SELECT count(*) FROM library_summary_outbox", Long.class); }

    private long seq(UUID user) { return jdbc.queryForObject("SELECT change_seq FROM library_summary_outbox WHERE user_id = ?", Long.class, user); }

    @Test void everyCollectionChangeMarksTheUserInTheSameTransactionAndCoalescesPerUser() {
        writer.write(USER, GAME_A, LibraryAction.COLLECT);
        assertEquals(1, markers());
        assertEquals(1, seq(USER));
        writer.write(USER, GAME_A, LibraryAction.FAVORITE);
        writer.write(USER, GAME_B, LibraryAction.COMPLETE);
        writer.clearFlag(USER, GAME_A, LibraryFlag.FAVORITE);
        writer.remove(USER, GAME_B);
        assertEquals(1, markers(), "kullanıcı başına tek işaret");
        assertEquals(5, seq(USER), "her değişiklik sürümü artırır");
        writer.write(OTHER, GAME_A, LibraryAction.COLLECT);
        assertEquals(2, markers());
    }

    @Test void failedWriteLeavesNoMarker() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> writer.write(null, GAME_A, LibraryAction.COLLECT));
        assertEquals(0, markers());
    }

    @Test void relaySendsCurrentAbsoluteCountsAndRemovesTheDeliveredMarker() {
        writer.write(USER, GAME_A, LibraryAction.FAVORITE);
        writer.write(USER, GAME_B, LibraryAction.COMPLETE);
        writer.write(OTHER, GAME_A, LibraryAction.COLLECT);
        assertEquals(2, relay.deliverDue());
        verify(publisher).publish(USER, 2, 1, 1);
        verify(publisher).publish(OTHER, 1, 0, 0);
        assertEquals(0, markers());
        assertEquals(0, relay.deliverDue(), "teslim edilenler yeniden gönderilmez");
    }

    @Test void deletingTheLastEntryStillSendsAnAbsoluteZero() {
        writer.write(USER, GAME_A, LibraryAction.COLLECT);
        relay.deliverDue();
        writer.remove(USER, GAME_A);
        assertEquals(1, relay.deliverDue());
        verify(publisher).publish(USER, 0, 0, 0);
    }

    @Test void relayIsInertWhenThePublisherIsNotReady() {
        when(publisher.ready()).thenReturn(false);
        writer.write(USER, GAME_A, LibraryAction.COLLECT);
        assertEquals(0, relay.deliverDue());
        verify(publisher, never()).publish(any(), anyInt(), anyInt(), anyInt());
        assertEquals(0, jdbc.queryForObject("SELECT attempts FROM library_summary_outbox", Integer.class), "deneme hakkı yanmaz");
        assertEquals(1, markers());
    }

    @Test void failedDeliveryKeepsTheMarkerBacksOffAndDoesNotBlockOtherUsers() {
        writer.write(USER, GAME_A, LibraryAction.COLLECT);
        writer.write(OTHER, GAME_A, LibraryAction.COLLECT);
        doThrow(new SummaryDeliveryException("boom")).when(publisher).publish(eq(USER), anyInt(), anyInt(), anyInt());
        assertEquals(1, relay.deliverDue());
        verify(publisher).publish(OTHER, 1, 0, 0);
        assertEquals(1, markers());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM library_summary_outbox WHERE user_id = ?", Integer.class, USER));
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at > clock_timestamp() FROM library_summary_outbox", Boolean.class),
                "başarısız işaret geri çekilir, hemen yeniden denenmez");
        assertEquals(0, relay.deliverDue());
    }

    @Test void backoffGrowsExponentiallyAndIsCapped() {
        writer.write(USER, GAME_A, LibraryAction.COLLECT);
        for (int attempt = 0; attempt < 4; attempt++) {
            jdbc.update("UPDATE library_summary_outbox SET next_attempt_at = clock_timestamp()");
            outbox.fail(USER, 5, 12);
        }
        assertEquals(4, jdbc.queryForObject("SELECT attempts FROM library_summary_outbox", Integer.class));
        double seconds = jdbc.queryForObject("SELECT extract(epoch FROM next_attempt_at - clock_timestamp()) FROM library_summary_outbox", Double.class);
        assertTrue(seconds > 8 && seconds <= 12, "5*2^3=40 sn değil, 12 sn üst sınıra kırpılır: " + seconds);
    }

    @Test void aChangeDuringDeliveryKeepsTheMarkerAndMakesItDueAgain() {
        writer.write(USER, GAME_A, LibraryAction.COLLECT);
        doAnswer(call -> {
            writer.write(USER, GAME_B, LibraryAction.FAVORITE); // yalnız ilk teslim sürerken koleksiyon değişti
            return null;
        }).doNothing().when(publisher).publish(eq(USER), anyInt(), anyInt(), anyInt());
        assertEquals(1, relay.deliverDue());
        assertEquals(1, markers(), "yeni sürüm silinmez");
        assertEquals(2, seq(USER));
        assertEquals(1, relay.deliverDue(), "değişiklik hemen yeniden zamanı gelen işarettir");
        verify(publisher).publish(USER, 2, 0, 1);
        assertEquals(0, markers());
    }

    @Test void aLeasedClaimIsNotHandedToAnotherRelayUntilTheLeaseExpires() {
        writer.write(USER, GAME_A, LibraryAction.COLLECT);
        assertEquals(1, outbox.claimDue(10, 60).size());
        assertEquals(0, outbox.claimDue(10, 60).size(), "kiradaki işaret ikinci relay'e verilmez");
        jdbc.update("UPDATE library_summary_outbox SET next_attempt_at = clock_timestamp() - interval '1 second'");
        assertEquals(1, outbox.claimDue(10, 60).size(), "kira dolunca çöken relay'in işareti yeniden alınır");
    }

    @Test void claimsRespectTheBatchSizeAndOrder() {
        for (int index = 0; index < 5; index++) writer.write(UUID.randomUUID(), GAME_A, LibraryAction.COLLECT);
        assertEquals(2, outbox.claimDue(2, 60).size());
        assertEquals(3, outbox.claimDue(10, 60).size());
    }

    @Test void accountErasureRemovesTheCollectionAndThePendingMarker() {
        writer.write(USER, GAME_A, LibraryAction.FAVORITE);
        writer.write(OTHER, GAME_A, LibraryAction.FAVORITE);
        erasure.erase(USER);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM library_entries WHERE user_id = ?", Integer.class, USER));
        assertEquals(List.of(OTHER), jdbc.queryForList("SELECT user_id FROM library_summary_outbox", UUID.class));
        assertEquals(1, relay.deliverDue());
        verify(publisher, never()).publish(eq(USER), anyInt(), anyInt(), anyInt());
    }

    @Test void v43MarksEveryExistingCollectionOwnerOnceForTheInitialTransfer() {
        var datasource = jdbc.getDataSource();
        try {
            var before = org.flywaydb.core.Flyway.configure().dataSource(datasource).defaultSchema("summary_upgrade")
                    .locations("classpath:db/migration").target("42").load();
            before.migrate();
            for (UUID user : List.of(USER, USER, OTHER)) {
                jdbc.update("INSERT INTO summary_upgrade.library_entries(id,user_id,game_id,favorite) VALUES (?,?,?,true)",
                        UUID.randomUUID(), user, UUID.randomUUID());
            }
            org.flywaydb.core.Flyway.configure().dataSource(datasource).defaultSchema("summary_upgrade")
                    .locations("classpath:db/migration").load().migrate();
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM summary_upgrade.library_summary_outbox", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM summary_upgrade.library_summary_outbox WHERE change_seq = 1 AND attempts = 0"
                    + " AND next_attempt_at <= clock_timestamp()", Integer.class));
            assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM summary_upgrade.library_entries", Integer.class), "koleksiyon değişmez");
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS summary_upgrade CASCADE");
        }
    }

    @Test void constraintsRejectInvalidRows() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO library_summary_outbox(user_id, change_seq) VALUES (?, 0)", USER));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO library_summary_outbox(user_id, attempts) VALUES (?, -1)", USER));
        assertFalse(markers() > 0);
    }

    private static UUID eq(UUID value) { return org.mockito.ArgumentMatchers.eq(value); }
}
