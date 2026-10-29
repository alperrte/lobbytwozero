package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import com.ltz.game.api.exception.GameExceptionHandler;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.AdminGameService;
import com.ltz.game.application.service.CatalogImportService;
import com.ltz.game.application.service.GameEdit;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gerçek eşzamanlılık testleri. Bu sınıf test-yönetimli transaction KULLANMAZ: her HTTP/servis çağrısı kendi gerçek
 * transaction'ında commit edilir veya geri alınır; iş parçacıkları ayrı bağlantılar kullanır. Veri yalnız testin kendi
 * PostgreSQL 17 Testcontainers veritabanındadır (geçici, geliştirici DB'si veya üretim değil); her test sonunda kendi
 * oluşturduğu kayıtları temizler. Havuz sınırı (3 bağlantı) bilinçli olarak üretimdeki gibi bırakılmıştır.
 */
@Import(AuthenticatedApiTest.Fakes.class)
@Transactional(propagation = Propagation.NEVER)
class ConcurrencyIntegrationTest extends AuthenticatedApiTest {
    static final String REASON = "Eşzamanlılık testi için yeterli gerekçe";
    static final Map<String, Object> OBSERVATIONS = new TreeMap<>();
    @Autowired CatalogImportService imports;
    @Autowired AdminGameService adminGames;
    @Autowired GameRepository games;
    @Autowired GameExternalReferenceRepository refs;
    @Autowired PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUpOwnData() {
        jdbc.execute("truncate game_admin_audits");
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        jdbc.update("delete from catalog_terms where code like 'conc-%'");
        provider.pages.clear();
    }

    // ------------------------------------------------------------------ yardımcılar

    <T> List<T> parallel(int count, IntFunction<Callable<T>> task) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            try {
                CountDownLatch ready = new CountDownLatch(count);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<T>> futures = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Callable<T> work = task.apply(i);
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return work.call();
                    }));
                }
                assertTrue(ready.await(20, TimeUnit.SECONDS));
                go.countDown();
                List<T> results = new ArrayList<>();
                for (Future<T> future : futures) results.add(future.get(90, TimeUnit.SECONDS));
                return results;
            } finally {
                pool.shutdownNow();
            }
        }
    }

    int statusOf(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return send(request, token, body).andReturn().getResponse().getStatus();
    }

    String createGame(String title) throws Exception {
        return read(send(post("/api/v1/game/admin/games"), admin(), "{\"title\":\"" + title + "\",\"gameType\":\"MAIN_GAME\"}")
                .andExpect(status().isCreated()), "$.gameId");
    }

    long version(String id) throws Exception {
        return Long.parseLong(read(send(get("/api/v1/game/admin/games/" + id), admin(), null).andExpect(status().isOk()), "$.version"));
    }

    long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    long audits(String gameId, String action) {
        return count("select count(*) from game_admin_audits where game_id=? and action=?", UUID.fromString(gameId), action);
    }

    String reasonBody() { return "{\"reason\":\"" + REASON + "\"}"; }

    long frequency(List<Integer> statuses, int wanted) { return statuses.stream().filter(value -> value == wanted).count(); }

    // ------------------------------------------------------------------ aynı oyunun eşzamanlı güncellenmesi

    @Test void concurrentUpdatesWithTheSameVersionAllowExactlyOneWinner() throws Exception {
        String id = createGame("Contended");
        long start = version(id);
        String token = admin();
        List<Integer> statuses = parallel(8, index -> () -> statusOf(patch("/api/v1/game/admin/games/" + id), token,
                "{\"version\":" + start + ",\"title\":\"Writer " + index + "\"}"));
        assertEquals(1, frequency(statuses, 200), "yalnız tek yazar kabul edilmeli: " + statuses);
        assertEquals(7, frequency(statuses, 409), "diğerleri sürüm çakışması almalı: " + statuses);
        assertEquals(1, audits(id, "UPDATE_GAME"), "reddedilen güncellemeler denetim kaydı bırakmamalı");
        assertTrue(version(id) > start);
        String winner = jdbc.queryForObject("select title from games where id=?", String.class, UUID.fromString(id));
        assertTrue(winner.startsWith("Writer "), winner);
        // Kaybedenlerin hiçbir değişikliği (başlık, normalize başlık) kısmen yazılmamış olmalı.
        assertEquals(winner.toLowerCase(), jdbc.queryForObject("select normalized_title from games where id=?", String.class, UUID.fromString(id)));
    }

    @Test void concurrentUpdatesThatRetryWithFreshVersionsSerializeWithoutLostUpdates() throws Exception {
        String id = createGame("Serialized");
        String token = admin();
        int writers = 6;
        List<Long> committedVersions = parallel(writers, index -> () -> {
            for (int attempt = 0; attempt < 40; attempt++) {
                long current = Long.parseLong(read(send(get("/api/v1/game/admin/games/" + id), token, null), "$.version"));
                var result = send(patch("/api/v1/game/admin/games/" + id), token, "{\"version\":" + current + ",\"description\":\"d-" + index + "\"}");
                int code = result.andReturn().getResponse().getStatus();
                if (code == 200) return Long.parseLong(read(result, "$.version"));
                assertEquals(409, code);
            }
            throw new AssertionError("güncelleme 40 denemede kabul edilmedi");
        });
        assertEquals(writers, committedVersions.stream().distinct().count(), "her kabul edilen güncelleme benzersiz bir sürüm üretmeli: " + committedVersions);
        assertEquals(writers, audits(id, "UPDATE_GAME"), "kabul edilen her güncelleme tam bir kez denetlenmeli");
        assertEquals(committedVersions.stream().max(Long::compare).orElseThrow(), version(id));
        assertTrue(jdbc.queryForObject("select description from games where id=?", String.class, UUID.fromString(id)).startsWith("d-"));
    }

    @Test void concurrentArchiveAndRestoreKeepStateAndAuditConsistent() throws Exception {
        String id = createGame("Toggled");
        String token = admin();
        List<Integer> archives = parallel(6, index -> () -> statusOf(post("/api/v1/game/admin/games/" + id + "/archive"), token, reasonBody()));
        assertEquals(6, frequency(archives, 200));
        assertEquals(1, audits(id, "ARCHIVE_GAME"), "aynı anda gelen tekrarlı arşivleme tek denetim kaydı bırakır");
        List<Integer> restores = parallel(6, index -> () -> statusOf(post("/api/v1/game/admin/games/" + id + "/restore"), token, reasonBody()));
        assertEquals(6, frequency(restores, 200));
        assertEquals(1, audits(id, "RESTORE_GAME"));
        // Karışık yarış: ardışık gerçek geçişler sayıca dengelidir ve son durum sayım farkıyla tutarlıdır.
        parallel(10, index -> () -> statusOf(post("/api/v1/game/admin/games/" + id + (index % 2 == 0 ? "/archive" : "/restore")), token, reasonBody()));
        long archived = audits(id, "ARCHIVE_GAME");
        long restored = audits(id, "RESTORE_GAME");
        String status = jdbc.queryForObject("select status from games where id=?", String.class, UUID.fromString(id));
        assertTrue(archived - restored == 0 || archived - restored == 1, archived + "/" + restored);
        assertEquals(archived > restored ? "ARCHIVED" : "ACTIVE", status);
    }

    // ------------------------------------------------------------------ geliştirici yetkisi ve içerik

    @Test void concurrentGrantsAreIdempotentAndConcurrentRevocationsSucceedExactlyOnce() throws Exception {
        String id = createGame("Granted");
        String token = admin();
        String path = "/api/v1/game/admin/games/" + id + "/developers";
        String body = "{\"developerUserId\":\"" + DEVELOPER_ID + "\",\"reason\":\"" + REASON + "\"}";
        List<Integer> grants = parallel(8, index -> () -> statusOf(post(path), token, body));
        assertEquals(8, frequency(grants, 200), grants.toString());
        assertEquals(1, count("select count(*) from game_developer_grants where game_id=? and revoked_at is null", UUID.fromString(id)));
        assertEquals(1, audits(id, "GRANT_DEVELOPER"));
        List<Integer> revokes = parallel(8, index -> () -> statusOf(post(path + "/" + DEVELOPER_ID + "/revoke"), token, reasonBody()));
        assertEquals(1, frequency(revokes, 204), revokes.toString());
        assertEquals(7, frequency(revokes, 404));
        assertEquals(0, count("select count(*) from game_developer_grants where game_id=? and revoked_at is null", UUID.fromString(id)));
        assertEquals(1, count("select count(*) from game_developer_grants where game_id=? and revoked_at is not null", UUID.fromString(id)));
        assertEquals(1, audits(id, "REVOKE_DEVELOPER"));
    }

    @Test void concurrentContentCreationRespectsLimitsAndSingleDescription() throws Exception {
        String id = createGame("Content race");
        send(post("/api/v1/game/admin/games/" + id + "/developers"), admin(),
                "{\"developerUserId\":\"" + DEVELOPER_ID + "\",\"reason\":\"" + REASON + "\"}").andExpect(status().isOk());
        String token = developer();
        String base = "/api/v1/game/developer/games/" + id;
        // DEMO sınırı 3: 10 eşzamanlı istekten tam 3'ü kabul edilir.
        List<Integer> media = parallel(10, index -> () -> statusOf(post(base + "/media"), token,
                "{\"type\":\"DEMO\",\"url\":\"https://store.steampowered.com/app/" + (100 + index) + "/Demo/\"}"));
        assertEquals(3, frequency(media, 201), media.toString());
        assertEquals(7, frequency(media, 409));
        assertEquals(3, count("select count(*) from game_developer_media where game_id=? and status<>'DELETED'", UUID.fromString(id)));
        // İlk açıklamayı eşzamanlı oluşturma: biri oluşturur, diğerleri sürüm çakışması alır.
        List<Integer> descriptions = parallel(6, index -> () -> statusOf(put(base + "/description"), token, "{\"description\":\"Açıklama " + index + "\"}"));
        assertEquals(1, frequency(descriptions, 200), descriptions.toString());
        assertEquals(5, frequency(descriptions, 409));
        assertEquals(1, count("select count(*) from game_developer_contents where game_id=?", UUID.fromString(id)));
    }

    @Test void concurrentModerationHideIsIdempotentAndAuditedOnce() throws Exception {
        String id = createGame("Moderated");
        send(post("/api/v1/game/admin/games/" + id + "/developers"), admin(),
                "{\"developerUserId\":\"" + DEVELOPER_ID + "\",\"reason\":\"" + REASON + "\"}").andExpect(status().isOk());
        send(put("/api/v1/game/developer/games/" + id + "/description"), developer(), "{\"description\":\"Metin\"}").andExpect(status().isOk());
        String hide = "{\"target\":\"DESCRIPTION\",\"action\":\"HIDE\",\"reason\":\"" + REASON + "\"}";
        String path = "/api/v1/game/moderation/games/" + id + "/developer-content/actions";
        String moderator = moderator();
        List<Integer> statuses = parallel(8, index -> () -> statusOf(post(path), moderator, hide));
        assertEquals(8, frequency(statuses, 200), statuses.toString());
        assertEquals(1, audits(id, "HIDE_DEVELOPER_CONTENT"));
        assertEquals("HIDDEN", jdbc.queryForObject("select status from game_developer_contents where game_id=?", String.class, UUID.fromString(id)));
    }

    // ------------------------------------------------------------------ editör listesi

    @Test void concurrentEditorialReplacementsLeaveExactlyOneCompleteList() throws Exception {
        List<String> ids = List.of(createGame("Editorial A"), createGame("Editorial B"), createGame("Editorial C"));
        String token = admin();
        List<Integer> statuses = parallel(8, index -> () -> {
            List<String> order = index % 2 == 0 ? ids : List.of(ids.get(2), ids.get(1), ids.get(0));
            String list = order.stream().map(value -> "\"" + value + "\"").reduce((a, b) -> a + "," + b).orElseThrow();
            return statusOf(put("/api/v1/game/admin/popular"), token, "{\"gameIds\":[" + list + "],\"reason\":\"Liste " + (index % 2) + " seçimi gerekçesi\"}");
        });
        assertEquals(8, frequency(statuses, 204), statuses.toString());
        List<Map<String, Object>> rows = jdbc.queryForList("select id, editor_rank, editor_evidence from games where editor_rank is not null order by editor_rank");
        assertEquals(3, rows.size());
        assertEquals(List.of(1, 2, 3), rows.stream().map(row -> ((Number) row.get("editor_rank")).intValue()).toList());
        assertEquals(1, rows.stream().map(row -> row.get("editor_evidence")).distinct().count(), "sıralar tek bir komuttan gelmeli (karışık liste yok)");
        List<String> actual = rows.stream().map(row -> row.get("id").toString()).toList();
        assertTrue(actual.equals(ids) || actual.equals(List.of(ids.get(2), ids.get(1), ids.get(0))), actual.toString());
    }

    // ------------------------------------------------------------------ harici referanslar ve senkronizasyon

    @Test void concurrentCatalogImportPagesNeverDuplicateTheExternalReference() throws Exception {
        int pages = 8;
        for (int i = 0; i < pages; i++) provider.page(10, "Steam Game", i < pages - 1);
        imports.startInitial();
        List<String> outcomes = parallel(pages, index -> () -> {
            try {
                imports.importNextPage(1000);
                return "OK";
            } catch (RuntimeException failure) {
                return failure.getClass().getSimpleName();
            }
        });
        assertEquals(pages, outcomes.stream().filter("OK"::equals).count(), outcomes.toString());
        assertEquals(1, count("select count(*) from game_external_refs where provider='STEAM' and external_id='10'"));
        assertEquals(1, count("select count(*) from games"));
        assertEquals(pages, count("select processed_count from game_provider_sync_state where provider='STEAM'"));
    }

    @Test void databaseUniquenessAlsoHoldsWhenSeparateTransactionsInsertTheSameExternalReference() throws Exception {
        List<UUID> gamesIds = new ArrayList<>();
        for (int i = 0; i < 8; i++) gamesIds.add(game("Dup " + i, "EXTERNAL", "MAIN_GAME", "ACTIVE"));
        List<Boolean> inserted = parallel(8, index -> () -> {
            try {
                jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (gen_random_uuid(),?,'STEAM','777')", gamesIds.get(index));
                return true;
            } catch (DuplicateKeyException _) {
                return false;
            }
        });
        assertEquals(1, inserted.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, count("select count(*) from game_external_refs where provider='STEAM' and external_id='777'"));
    }

    @Test void adminCorrectionAndCatalogSyncRacingOnTheSameExternalGameNeverLoseTheAdminTitle() throws Exception {
        String token = admin();
        AtomicInteger syncConflicts = new AtomicInteger();
        AtomicInteger adminConflicts = new AtomicInteger();
        int iterations = 30;
        for (int i = 0; i < iterations; i++) {
            long appId = 5000 + i;
            jdbc.update("update game_provider_sync_state set cursor=0,sync_status='IDLE',run_mode=null,query_since=null,run_started_at=null,"
                    + "last_successful_at=null,processed_count=0,last_error_code=null,failure_count=0,next_attempt_at=null");
            provider.page(appId, "Original " + i, false);
            imports.startInitial();
            imports.importNextPage(1000);
            String id = refs.findByProviderAndExternalId("STEAM", "" + appId).orElseThrow().getGame().getId().toString();
            provider.page(appId, "Steam " + i, false);
            imports.startInitial();
            int round = i;
            List<String> outcomes = parallel(2, index -> () -> {
                // Başlangıç sırası turdan tura değişsin: bazen yönetici, bazen senkronizasyon önce başlar, bazen tam iç içe geçer.
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(index == 0 ? (round % 5) * 3L : ((round + 2) % 5) * 3L));
                if (index == 1) {
                    try {
                        imports.importNextPage(1000);
                        return "SYNC_OK";
                    } catch (ObjectOptimisticLockingFailureException _) {
                        syncConflicts.incrementAndGet();
                        return "SYNC_CONFLICT";
                    }
                }
                for (int attempt = 0; attempt < 10; attempt++) {
                    long current = Long.parseLong(read(send(get("/api/v1/game/admin/games/" + id), token, null), "$.version"));
                    int code = statusOf(patch("/api/v1/game/admin/games/" + id), token, "{\"version\":" + current + ",\"title\":\"Admin " + round + "\"}");
                    if (code == 200) return "ADMIN_OK";
                    assertEquals(409, code);
                    adminConflicts.incrementAndGet();
                }
                return "ADMIN_FAILED";
            });
            assertTrue(outcomes.contains("ADMIN_OK"), outcomes.toString());
            // Eşzamanlı senkronizasyon çakışırsa sonraki turda yeniden denenir; yönetici başlığı yine korunmalıdır.
            if (outcomes.contains("SYNC_CONFLICT")) {
                jdbc.update("update game_provider_sync_state set sync_status='IDLE',cursor=0,run_mode=null,query_since=null,run_started_at=null,processed_count=0");
                provider.page(appId, "Steam retry " + i, false);
                imports.startInitial();
                imports.importNextPage(1000);
            }
            assertEquals("Admin " + i, jdbc.queryForObject("select title from games where id=?", String.class, UUID.fromString(id)), outcomes.toString());
            assertEquals(1, count("select count(*) from game_field_overrides where game_id=? and field='TITLE'", UUID.fromString(id)));
        }
        OBSERVATIONS.put("adminVsSyncIterations", iterations);
        OBSERVATIONS.put("syncOptimisticConflicts", syncConflicts.get());
        OBSERVATIONS.put("adminVersionConflictsRetried", adminConflicts.get());
        writeObservations();
    }

    // ------------------------------------------------------------------ iyimser kilit, rollback, commit

    @Test void optimisticLockingRejectsAStaleWriterAndLeavesNoPartialChange() throws Exception {
        UUID id = UUID.fromString(createGame("Optimistic"));
        var tx = new TransactionTemplate(transactionManager);
        var stale = tx.execute(status -> games.findById(id).orElseThrow());
        long loaded = stale.getVersion();
        tx.executeWithoutResult(status -> games.findById(id).orElseThrow().setDescription("winner"));
        assertEquals(loaded + 1, jdbc.queryForObject("select version from games where id=?", Long.class, id));
        stale.setDescription("loser");
        stale.setTitle("Loser title");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> tx.executeWithoutResult(status -> {
            games.saveAndFlush(stale);
        }));
        assertEquals("winner", jdbc.queryForObject("select description from games where id=?", String.class, id));
        assertEquals("Optimistic", jdbc.queryForObject("select title from games where id=?", String.class, id));
        assertEquals(loaded + 1, jdbc.queryForObject("select version from games where id=?", Long.class, id));
        // HTTP katmanı bu çakışmayı 409'a çevirir ve ayrıntı sızdırmaz.
        var problem = new GameExceptionHandler().concurrentModification();
        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("CONCURRENT_MODIFICATION", problem.getProperties().get("code"));
    }

    @Test void failedTransactionsRollBackEverythingIncludingNewTermsAndAudits() {
        Actor actor = Actor.of(ADMIN_ID, Set.of(ActorRole.ADMIN));
        // Terim önce yazılır (flush), medya doğrulaması sonra reddeder: hiçbiri kalmamalı.
        var edit = new GameEdit("Rolled back", null, null, null, GameType.MAIN_GAME, null, null, null, Set.of("conc-rollback"), null, null, null, "https://evil.example/c.jpg");
        assertThrows(InvalidGameRequestException.class, () -> adminGames.create(actor, edit));
        assertEquals(0, count("select count(*) from games where title='Rolled back'"));
        assertEquals(0, count("select count(*) from catalog_terms where code='conc-rollback'"));
        assertEquals(0, count("select count(*) from game_admin_audits"));
    }

    @Test void committedChangesAreVisibleToOtherConnectionsAndStaleVersionsLeaveNoTrace() throws Exception {
        String id = createGame("Committed");
        long start = version(id);
        assertEquals(1, audits(id, "CREATE_GAME"));
        assertEquals(409, statusOf(patch("/api/v1/game/admin/games/" + id), admin(), "{\"version\":" + (start + 5) + ",\"title\":\"Never\"}"));
        assertEquals("Committed", jdbc.queryForObject("select title from games where id=?", String.class, UUID.fromString(id)));
        assertEquals(0, audits(id, "UPDATE_GAME"));
        assertEquals(start, version(id));
        // Doğrulama hatası da transaction'ı geri alır: yeni terim ve kısmi güncelleme kalmaz.
        assertEquals(400, statusOf(patch("/api/v1/game/admin/games/" + id), admin(),
                "{\"version\":" + start + ",\"title\":\"Half\",\"genres\":[\"conc-half\"],\"coverUrl\":\"https://evil.example/c.jpg\"}"));
        assertEquals("Committed", jdbc.queryForObject("select title from games where id=?", String.class, UUID.fromString(id)));
        assertEquals(0, count("select count(*) from catalog_terms where code='conc-half'"));
        assertEquals(start, version(id));
    }

    private void writeObservations() throws Exception {
        StringBuilder json = new StringBuilder("{");
        OBSERVATIONS.forEach((key, value) -> json.append(json.length() > 1 ? "," : "").append('"').append(key).append("\":").append(value));
        Files.writeString(Path.of("target", "concurrency-observations.json"), json.append('}').toString());
    }
}
