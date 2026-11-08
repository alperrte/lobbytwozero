package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.CatalogRunMode;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataCompleteness;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest @Import(PhaseTwoCatalogTest.Fakes.class)
class PhaseTwoCatalogTest extends PostgresTestSupport {
    @TestConfiguration static class Fakes {
        @Bean @Primary FakeProvider phaseTwoProvider() { return new FakeProvider(); }
        @Bean @Primary MutableClock phaseTwoClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    static class FakeProvider implements GameProvider {
        boolean supported = true;
        final Queue<Supplier<CatalogPage>> pages = new ConcurrentLinkedQueue<>();
        final List<CatalogQuery> queries = new CopyOnWriteArrayList<>();
        final Queue<Supplier<Details>> details = new ConcurrentLinkedQueue<>();
        int detailCalls;
        String source;
        final java.util.Map<String, String> typeEvidence = new java.util.HashMap<>();
        final List<List<String>> typeQueries = new CopyOnWriteArrayList<>();
        String typeFailure;
        final java.util.Map<String, LocalDate> dateEvidence = new java.util.HashMap<>();
        final java.util.Map<String, List<TermEvidence>> genreEvidence = new java.util.HashMap<>();
        final java.util.Map<String, List<TermEvidence>> platformEvidence = new java.util.HashMap<>();
        final java.util.Map<String, List<TermEvidence>> categoryEvidence = new java.util.HashMap<>();
        final java.util.Map<String, List<TermEvidence>> tagEvidence = new java.util.HashMap<>();
        @Override public java.util.Map<String, ItemEvidence> itemEvidence(java.util.Collection<String> ids) {
            typeQueries.add(List.copyOf(ids));
            if (typeFailure != null) throw new ProviderException(typeFailure);
            java.util.Map<String, ItemEvidence> found = new java.util.HashMap<>();
            ids.forEach(id -> {
                if (typeEvidence.containsKey(id) || dateEvidence.containsKey(id) || genreEvidence.containsKey(id) || platformEvidence.containsKey(id)
                        || categoryEvidence.containsKey(id) || tagEvidence.containsKey(id))
                    found.put(id, new ItemEvidence(typeEvidence.get(id), dateEvidence.get(id), genreEvidence.get(id), platformEvidence.get(id),
                            categoryEvidence.get(id), tagEvidence.get(id)));
            });
            return found;
        }
        @Override public String detailSource() { return source == null ? provider() : source; }
        void detailWith(GameProvider.LanguageSupport... languages) {
            details.add(() -> new Details("Provider description", LocalDate.of(2020, 1, 1), Set.of(), Set.of(), null, List.of(languages)));
        }
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return supported ? Set.of(Capability.CATALOG, Capability.INCREMENTAL_CATALOG, Capability.DETAILS, Capability.ITEM_TYPE) : Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { queries.add(query); return Objects.requireNonNull(pages.poll()).get(); }
        @Override public Details details(String id) { detailCalls++; return Objects.requireNonNull(details.poll()).get(); }
        void pageAt(long id, String title, Instant lastModified, boolean more) {
            pages.add(() -> new CatalogPage(List.of(new CatalogItem("" + id, title, lastModified)), id, more));
        }
        void page(long id, boolean more) { pages.add(() -> new CatalogPage(List.of(new CatalogItem("" + id, "Example " + id, Instant.EPOCH)), id, more)); }
        void fail(String code) { pages.add(() -> { throw new ProviderException(code); }); }
        void detail() { details.add(() -> new Details("Local snapshot", LocalDate.of(2020, 1, 1), Set.of("Developer"), Set.of("Publisher"), "game")); }
    }
    @Autowired FakeProvider provider;
    @Autowired MutableClock clock;
    @Autowired CatalogImportService imports;
    @Autowired GameDetailRefreshService refresh;
    @Autowired CatalogClassificationService classification;
    @Autowired GameViewService views;
    @Autowired GameRepository games;
    @Autowired GameExternalReferenceRepository refs;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    org.springframework.test.web.servlet.MockMvc mvc;

    @BeforeEach void reset() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        clock.now = Instant.parse("2026-10-21T00:00:00Z");
        provider.supported = true; provider.pages.clear(); provider.queries.clear(); provider.details.clear(); provider.detailCalls = 0; provider.source = null;
        provider.typeEvidence.clear(); provider.dateEvidence.clear(); provider.genreEvidence.clear(); provider.platformEvidence.clear(); provider.categoryEvidence.clear(); provider.tagEvidence.clear(); provider.typeQueries.clear(); provider.typeFailure = null;
        classification.restartPassForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games"); jdbc.update("delete from catalog_terms");
        jdbc.update("update game_provider_sync_state set cursor=0,sync_status='IDLE',run_mode=null,query_since=null,run_started_at=null,last_successful_at=null,processed_count=0,last_error_code=null,failure_count=0,next_attempt_at=null");
    }
    UUID indexed() {
        provider.page(10, false); imports.synchronizeNextPage();
        return refs.findByProviderAndExternalId("STEAM", "10").orElseThrow().getGame().getId();
    }
    @Test void automaticInitialAndIncrementalRespectIntervalAndRestartedCheckpoint() {
        provider.page(10, true); imports.synchronizeNextPage();
        assertEquals(10, imports.status().cursor());
        provider.page(20, false); imports.synchronizeNextPage();
        assertEquals(List.of(0L,10L),provider.queries.stream().map(GameProvider.CatalogQuery::afterAppId).toList());
        imports.synchronizeNextPage(); assertEquals(2, provider.queries.size());
        clock.advance(Duration.ofHours(23)); imports.synchronizeNextPage(); assertEquals(2, provider.queries.size(), "günlük pencere dolmadan yeni tur başlamaz");
        clock.advance(Duration.ofHours(1)); provider.page(20, false); imports.synchronizeNextPage();
        assertEquals(CatalogRunMode.INCREMENTAL, imports.status().runMode());
        assertEquals(Instant.parse("2026-10-20T23:59:59Z"), provider.queries.getLast().modifiedSince());
        assertEquals(2, games.count());
    }
    @Test void retryRetainsCursorAndWatermarkAndDoesNotCallBeforeBackoff() {
        provider.page(10, true); imports.synchronizeNextPage();
        provider.fail("PROVIDER_UNAVAILABLE"); imports.synchronizeNextPage();
        assertEquals(1, imports.status().failureCount());
        assertEquals(clock.instant().plusSeconds(5), imports.status().nextAttemptAt());
        imports.synchronizeNextPage(); assertEquals(2, provider.queries.size());
        clock.advance(Duration.ofSeconds(5)); provider.page(20, false); imports.synchronizeNextPage();
        assertEquals(10, provider.queries.getLast().afterAppId());
        assertEquals(2, games.count()); assertEquals(0, imports.status().failureCount());
        assertNull(imports.status().lastErrorCode());
    }
    @Test void retryBudgetIsPersistentAndManualResumePreservesProgress() {
        provider.page(10, true); imports.synchronizeNextPage();
        for (int i = 1; i <= 6; i++) { provider.fail("PROVIDER_UNAVAILABLE"); imports.synchronizeNextPage(); clock.advance(Duration.ofHours(1)); }
        assertEquals(6, imports.status().failureCount()); assertNull(imports.status().nextAttemptAt());
        imports.synchronizeNextPage(); assertEquals(7, provider.queries.size());
        imports.resumeFailed(); provider.page(20, false); imports.importNextPage(1);
        assertEquals(10, provider.queries.getLast().afterAppId()); assertEquals(2, games.count());
        assertThrows(IllegalStateException.class, imports::resumeFailed);
    }
    @Test void accessAndSchemaErrorsDoNotRetryAutomatically() {
        for (String code : List.of("PROVIDER_ACCESS_DENIED", "INVALID_PROVIDER_RESPONSE", "PROVIDER_NOT_CONFIGURED")) {
            imports.startInitial(); provider.fail(code); imports.importNextPage(1);
            assertNull(imports.status().nextAttemptAt());
            int calls = provider.queries.size(); clock.advance(Duration.ofDays(2)); imports.synchronizeNextPage();
            assertEquals(calls, provider.queries.size());
        }
    }
    @Test void onDemandRefreshCachesPartialDataAndLocalReadsNeverCallProvider() {
        UUID id = indexed(); assertEquals("MISSING", views.detail(id).freshness().status());
        assertEquals(0, provider.detailCalls); provider.detail();
        assertEquals("REFRESHED", refresh.refresh(id)); assertEquals("FRESH", refresh.refresh(id));
        var detail = views.detail(id);
        assertEquals("Local snapshot", detail.description()); assertEquals(GameType.MAIN_GAME, detail.game().gameType());
        assertEquals(MetadataCompleteness.PARTIAL, detail.game().metadataCompleteness());
        assertEquals(clock.instant().plus(Duration.ofHours(24)), detail.freshness().expiresAt());
        assertEquals(1, provider.detailCalls); assertTrue(refresh.dueCandidates().isEmpty());
        clock.advance(Duration.ofHours(24)); assertEquals("STALE", views.detail(id).freshness().status());
        assertEquals(List.of(id), refresh.dueCandidates());
        assertEquals(1, provider.detailCalls);
    }
    @Test void staleDataSurvivesProviderFailureAndRetriesRespectDueTime() {
        UUID id = indexed(); provider.detail(); refresh.refresh(id); clock.advance(Duration.ofDays(1));
        provider.details.add(() -> { throw new ProviderException("RATE_LIMITED", Duration.ofMinutes(10)); });
        assertEquals("FAILED", refresh.refresh(id)); assertEquals("BACKOFF", refresh.refresh(id));
        assertEquals("Local snapshot", views.detail(id).description());
        assertEquals("STALE", views.detail(id).freshness().status()); assertTrue(refresh.dueCandidates().isEmpty());
        clock.advance(Duration.ofMinutes(10)); provider.detail(); assertEquals("REFRESHED", refresh.refresh(id));
        assertNull(views.detail(id).freshness().lastErrorCode());
    }
    @Test void permanentDetailFailureRequiresExplicitReset() {
        UUID id = indexed(); provider.details.add(() -> { throw new ProviderException("PROVIDER_ACCESS_DENIED"); });
        assertEquals("FAILED", refresh.refresh(id)); clock.advance(Duration.ofDays(7));
        assertEquals("STOPPED", refresh.refresh(id)); assertTrue(refresh.dueCandidates().isEmpty());
        refresh.resetFailure(id); provider.detail(); assertEquals("REFRESHED", refresh.refresh(id));
    }
    @Test void unsupportedAndInternalDetailsDoNotInventMetadataOrCallProvider() {
        UUID id = indexed(); provider.supported = false;
        assertEquals("UNSUPPORTED", refresh.refresh(id)); assertEquals("UNSUPPORTED", views.detail(id).freshness().status());
        assertNull(views.detail(id).description()); assertTrue(refresh.dueCandidates().isEmpty());
        jdbc.update("update games set origin='INTERNAL' where id=?",id);
        assertEquals("LOCAL", refresh.refresh(id)); assertEquals("LOCAL", views.detail(id).freshness().status());
        assertEquals(0,provider.detailCalls);
    }
    @Test void concurrentDetailRequestsOnlyFetchOnce() throws Exception {
        UUID id = indexed(); provider.detail();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> refresh.refresh(id)); var two = executor.submit(() -> refresh.refresh(id));
            assertEquals(Set.of("REFRESHED", "FRESH"), Set.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS)));
        }
        assertEquals(1,provider.detailCalls);
    }
    @Test void missingFieldsPreservePreviousValuesAndInactiveGamesCannotRefresh() {
        UUID id = indexed(); provider.detail(); refresh.refresh(id); clock.advance(Duration.ofDays(1));
        provider.details.add(() -> new GameProvider.Details(null,null,Set.of("Other"),Set.of(),null));
        refresh.refresh(id); assertEquals("Local snapshot",views.detail(id).description());
        assertEquals(LocalDate.of(2020,1,1), views.detail(id).releaseDate());
        jdbc.update("update games set status='ARCHIVED' where id=?",id);
        assertThrows(GameNotFoundException.class, () -> refresh.refresh(id));
        assertThrows(GameNotFoundException.class, () -> refresh.resetFailure(id));
    }
    @Test void publicDetailGetIsReadOnlyAndExposesStaleSnapshot() throws Exception {
        UUID id = indexed(); provider.detail(); refresh.refresh(id); clock.advance(Duration.ofDays(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/game/games/"+id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.freshness.status").value("STALE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.description").value("Local snapshot"));
        assertEquals(1,provider.detailCalls);
        assertEquals(0, games.findById(id).orElseThrow().getDetailsFailureCount());
    }
    @Test void editorSelectedGamesArePrioritizedWithoutInventingDetails() {
        UUID id=indexed();
        jdbc.update("update games set editor_rank=1 where id=?",id);
        assertEquals(List.of(id),refresh.dueCandidates());
        provider.supported=false;
        assertTrue(refresh.dueCandidates().isEmpty());
        assertEquals(0,provider.detailCalls);
    }

    // ---- GAME-17/23: ayrı detay kaynağından (IGDB) dil desteği
    private static GameProvider.LanguageSupport english() {
        return new GameProvider.LanguageSupport("en-us", com.ltz.game.domain.enums.LanguageSupportStatus.SUPPORTED,
                com.ltz.game.domain.enums.LanguageSupportStatus.SUPPORTED, com.ltz.game.domain.enums.LanguageSupportStatus.UNKNOWN);
    }
    private static GameProvider.LanguageSupport turkishSubtitles() {
        return new GameProvider.LanguageSupport("tr-tr", com.ltz.game.domain.enums.LanguageSupportStatus.UNKNOWN,
                com.ltz.game.domain.enums.LanguageSupportStatus.UNKNOWN, com.ltz.game.domain.enums.LanguageSupportStatus.SUPPORTED);
    }
    private List<String> languageRows(UUID id) {
        return jdbc.queryForList("select language_code||':'||interface_support||':'||audio_support||':'||subtitle_support "
                + "from game_language_supports where game_id=? order by language_code", String.class, id);
    }
    private List<String> flatLanguages(UUID id) {
        return jdbc.queryForList("select language_code from game_languages where game_id=? order by language_code", String.class, id);
    }
    @Test void detailSourceLanguagesAreStoredWithSourceProvenanceAndNothingIsInventedAsUnsupported() {
        provider.source = "IGDB";
        UUID id = indexed();
        provider.detailWith(english(), turkishSubtitles());
        assertEquals("REFRESHED", refresh.refresh(id));
        assertEquals(List.of("en-us:SUPPORTED:SUPPORTED:UNKNOWN", "tr-tr:UNKNOWN:UNKNOWN:SUPPORTED"), languageRows(id));
        assertEquals("AVAILABLE", jdbc.queryForObject("select language_data_state from games where id=?", String.class, id));
        assertEquals(List.of("en-us", "tr-tr"), flatLanguages(id));
        for (String field : List.of("LOCALIZATION", "LANGUAGES", "DESCRIPTION", "RELEASE_DATE")) {
            assertEquals("IGDB", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field=?", String.class, id, field), field);
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from game_language_supports where game_id=? and (interface_support='UNSUPPORTED' "
                + "or audio_support='UNSUPPORTED' or subtitle_support='UNSUPPORTED')", Integer.class, id));
    }
    @Test void manuallyLockedLocalizationIsNotOverwrittenByTheDetailSource() {
        UUID id = indexed();
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at) values (?,?,'LOCALIZATION',?,now())",
                UUID.randomUUID(), id, UUID.randomUUID());
        provider.detailWith(english());
        assertEquals("REFRESHED", refresh.refresh(id));
        assertTrue(languageRows(id).isEmpty());
        assertEquals("UNKNOWN", jdbc.queryForObject("select language_data_state from games where id=?", String.class, id));
    }
    @Test void detailsWithoutLanguagesKeepExistingLanguageData() {
        UUID id = indexed();
        provider.detailWith(english());
        assertEquals("REFRESHED", refresh.refresh(id));
        clock.advance(Duration.ofDays(2));
        provider.detail(); // dil bilgisi yok: "dil yok" anlamına gelmez, mevcut veri silinmez
        assertEquals("REFRESHED", refresh.refresh(id));
        assertEquals(List.of("en-us:SUPPORTED:SUPPORTED:UNKNOWN"), languageRows(id));
        assertEquals("AVAILABLE", jdbc.queryForObject("select language_data_state from games where id=?", String.class, id));
    }
    @Test void flatLanguageListIsFilledOnlyWhenEmptyAndAdminDataIsNeverReplaced() {
        UUID id = indexed();
        jdbc.update("insert into game_languages(game_id,language_code) values (?,'tr')", id);
        provider.detailWith(english());
        assertEquals("REFRESHED", refresh.refresh(id));
        assertEquals(List.of("tr"), flatLanguages(id));
        assertEquals(1, languageRows(id).size());
    }

    // ---- Kalıcı Steam senkronu ve gerçek sağlayıcı kanıtıyla otomatik sınıflandırma
    private static GameProvider.Details typed(String type) {
        return new GameProvider.Details("Evidence", null, Set.of(), Set.of(), type);
    }
    @Test void skippedRecordsAdvanceTheCursorAreNotCountedAsProcessedAndReimportIsIdempotent() {
        provider.pages.add(() -> new GameProvider.CatalogPage(List.of(new GameProvider.CatalogItem("10", "Good", Instant.EPOCH),
                new GameProvider.CatalogItem("30", "Also good", Instant.EPOCH)), 30, false, 1));
        imports.synchronizeNextPage();
        var status = imports.status();
        assertEquals(30, status.cursor()); assertEquals(2, status.processedCount());
        assertEquals(com.ltz.game.domain.enums.CatalogSyncStatus.COMPLETE, status.syncStatus());
        assertEquals(2, refs.count()); assertEquals(2, games.count());
        // Aynı sayfanın tekrar işlenmesi kopya üretmez.
        clock.advance(Duration.ofHours(6));
        provider.pages.add(() -> new GameProvider.CatalogPage(List.of(new GameProvider.CatalogItem("10", "Good", Instant.EPOCH),
                new GameProvider.CatalogItem("30", "Also good", Instant.EPOCH)), 30, false, 1));
        imports.synchronizeNextPage();
        assertEquals(2, refs.count()); assertEquals(2, games.count());
    }
    @Test void aStuckFailedSyncResumesFromTheSameCursorAfterARestartExactlyOnce() {
        provider.page(10, true); imports.synchronizeNextPage();
        provider.fail("INVALID_PROVIDER_RESPONSE"); imports.synchronizeNextPage();
        var failed = imports.status();
        assertEquals(com.ltz.game.domain.enums.CatalogSyncStatus.FAILED, failed.syncStatus()); assertNull(failed.nextAttemptAt()); assertEquals(10, failed.cursor());
        assertTrue(imports.resumeAfterRestart());
        assertEquals(com.ltz.game.domain.enums.CatalogSyncStatus.RUNNING, imports.status().syncStatus()); assertEquals(10, imports.status().cursor());
        assertEquals(0, imports.status().failureCount()); assertNull(imports.status().lastErrorCode());
        assertFalse(imports.resumeAfterRestart(), "RUNNING aktarım yeniden başlatılmaz");
        provider.page(20, false); imports.synchronizeNextPage();
        assertEquals(10, provider.queries.getLast().afterAppId()); assertEquals(2, games.count());
        assertEquals(com.ltz.game.domain.enums.CatalogSyncStatus.COMPLETE, imports.status().syncStatus());
        assertFalse(imports.resumeAfterRestart(), "tamamlanmış aktarım yeniden başlatılmaz");
    }
    @Test void detailBootstrapCandidatesAreOnlyUntriedEvidenceBackedMainGamesOfActiveSteamGames() {
        UUID id = indexed();
        assertTrue(refresh.detailBootstrapCandidates(5).isEmpty(), "UNKNOWN kayıt (kanıt yok) detay bootstrap'ına girmez");
        jdbc.update("update games set game_type='MAIN_GAME' where id=?", id);
        assertEquals(List.of(id), refresh.detailBootstrapCandidates(5));
        assertTrue(refresh.detailBootstrapCandidates(0).isEmpty());
        provider.supported = false;
        assertTrue(refresh.detailBootstrapCandidates(5).isEmpty(), "detay sağlayıcısı yoksa aday yok");
        provider.supported = true;
        jdbc.update("update games set status='ARCHIVED' where id=?", id);
        assertTrue(refresh.detailBootstrapCandidates(5).isEmpty(), "arşivlenmiş kayıt işlenmez");
        jdbc.update("update games set status='ACTIVE', origin='INTERNAL' where id=?", id);
        assertTrue(refresh.detailBootstrapCandidates(5).isEmpty(), "INTERNAL kayıt aday değildir");
        jdbc.update("update games set origin='EXTERNAL' where id=?", id);
        provider.detail(); assertEquals("REFRESHED", refresh.refresh(id));
        assertTrue(refresh.detailBootstrapCandidates(5).isEmpty(), "denenen kayıt bootstrap kuyruğundan çıkar");
    }
    @Test void detailBootstrapProcessesNewestSyncedGameFirst() {
        provider.pages.add(() -> new GameProvider.CatalogPage(List.of(new GameProvider.CatalogItem("1", "Older", Instant.EPOCH)), 1, true));
        imports.synchronizeNextPage();
        jdbc.update("update games set created_at = created_at - interval '1 hour'");
        provider.pages.add(() -> new GameProvider.CatalogPage(List.of(new GameProvider.CatalogItem("2", "Newer", Instant.EPOCH)), 2, false));
        imports.synchronizeNextPage();
        jdbc.update("update games set game_type='MAIN_GAME'");
        UUID newer = refs.findByProviderAndExternalId("STEAM", "2").orElseThrow().getGame().getId();
        assertEquals(List.of(newer), refresh.detailBootstrapCandidates(1));
    }
    private void unknownSteamGames(int count) {
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) "
                + "select gen_random_uuid(), 'G' || n, 'g' || n, 'EXTERNAL','UNKNOWN','INDEX_ONLY',now(),now() from generate_series(1,?) n", count);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) "
                + "select gen_random_uuid(), g.id, 'STEAM', substr(g.title,2), now() from games g");
    }
    private GameType typeOf(String steamId) {
        return games.findById(refs.findByProviderAndExternalId("STEAM", steamId).orElseThrow().getGame().getId()).orElseThrow().getGameType();
    }
    @Test void bulkClassificationUsesOnlyProviderEvidenceAndLeavesTheRestUnknown() {
        unknownSteamGames(4);
        provider.typeEvidence.put("1", "main_game"); provider.typeEvidence.put("2", "dlc"); provider.typeEvidence.put("3", "weird-new-type");
        assertEquals(2, classification.classifyNextBatch());
        assertEquals(GameType.MAIN_GAME, typeOf("1")); assertEquals(GameType.DLC, typeOf("2"));
        assertEquals(GameType.UNKNOWN, typeOf("3")); assertEquals(GameType.UNKNOWN, typeOf("4"));
        assertEquals(List.of(List.of("4", "3", "2", "1")), provider.typeQueries, "en yeni AppID önce");
        assertEquals("STEAM", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='GAME_TYPE'", String.class,
                refs.findByProviderAndExternalId("STEAM", "1").orElseThrow().getGame().getId()));
        UUID noEvidence = refs.findByProviderAndExternalId("STEAM", "4").orElseThrow().getGame().getId();
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='GAME_TYPE' and data_state='AVAILABLE' and game_id=?", Long.class, noEvidence));
        assertEquals(1L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='GAME_TYPE' and data_state='UNKNOWN' and game_id=?", Long.class, noEvidence),
                "kanıtsız kayıt UNKNOWN gözlemiyle beklemeye alınır");
        assertEquals(1L, jdbc.queryForObject("select count(*) from games where game_type='MAIN_GAME'", Long.class));
    }
    @Test void bulkClassificationFinishesThePassPausesAndThenCatchesLateArrivals() {
        unknownSteamGames(2);
        provider.typeEvidence.put("1", "main_game");
        assertEquals(1, classification.classifyNextBatch());
        assertEquals(-1, classification.classifyNextBatch(), "tarama bitti");
        int queries = provider.typeQueries.size();
        assertEquals(-1, classification.classifyNextBatch(), "tur arası duraklama sağlayıcıya sormaz");
        assertEquals(queries, provider.typeQueries.size());
        provider.typeEvidence.put("2", "main_game");
        clock.advance(CatalogClassificationService.PASS_INTERVAL.plusMinutes(1));
        assertEquals(-1, classification.classifyNextBatch(), "kanıtsız kayıt bekleme süresi dolmadan yeniden sorulmaz");
        clock.advance(CatalogClassificationService.RECHECK_AFTER.plusHours(1));
        assertEquals(1, classification.classifyNextBatch());
        assertEquals(GameType.MAIN_GAME, typeOf("2"));
    }
    @Test void bulkClassificationPagesThroughTheWholeCatalogueByAppIdCursor() {
        unknownSteamGames(CatalogClassificationService.BATCH + 10);
        for (int id = 1; id <= CatalogClassificationService.BATCH + 10; id++) provider.typeEvidence.put(Integer.toString(id), "main_game");
        assertEquals(CatalogClassificationService.BATCH, classification.classifyNextBatch());
        assertEquals(10, classification.classifyNextBatch());
        assertEquals(CatalogClassificationService.BATCH + 10L, jdbc.queryForObject("select count(*) from games where game_type='MAIN_GAME'", Long.class));
        assertEquals(CatalogClassificationService.BATCH, provider.typeQueries.getFirst().size());
        assertEquals("260", provider.typeQueries.get(0).getFirst(), "ilk adım en yeni AppID'den başlar");
        assertEquals("1", provider.typeQueries.get(1).getFirst(), "ikinci adım en eski uçtan (artan) devam eder; iki uç dönüşümlü ilerler");
    }
    @Test void bulkClassificationRespectsManualLockAndIgnoresArchivedInternalAndAlreadyClassifiedGames() {
        unknownSteamGames(5);
        var lockedId = refs.findByProviderAndExternalId("STEAM", "1").orElseThrow().getGame().getId();
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at) values (?,?,'GAME_TYPE',?,now())", UUID.randomUUID(), lockedId, UUID.randomUUID());
        jdbc.update("update games set status='ARCHIVED' where id=?", refs.findByProviderAndExternalId("STEAM", "2").orElseThrow().getGame().getId());
        jdbc.update("update games set origin='INTERNAL' where id=?", refs.findByProviderAndExternalId("STEAM", "3").orElseThrow().getGame().getId());
        jdbc.update("update games set game_type='SOFTWARE' where id=?", refs.findByProviderAndExternalId("STEAM", "4").orElseThrow().getGame().getId());
        for (String id : List.of("1", "2", "3", "4", "5")) provider.typeEvidence.put(id, "main_game");
        assertEquals(1, classification.classifyNextBatch(), "kilitli oyun da aday olarak sorulur ama değiştirilmez");
        assertEquals(List.of(List.of("5", "1")), provider.typeQueries);
        assertEquals(GameType.UNKNOWN, typeOf("1")); assertEquals(GameType.SOFTWARE, typeOf("4")); assertEquals(GameType.MAIN_GAME, typeOf("5"));
        assertEquals(GameType.UNKNOWN, typeOf("2")); assertEquals(GameType.UNKNOWN, typeOf("3"));
    }
    @Test void bulkClassificationPausesWithGrowingBackoffOnProviderFailureWithoutChangingDataAndResetsOnSuccess() {
        unknownSteamGames(2); provider.typeEvidence.put("1", "main_game");
        provider.typeFailure = "RATE_LIMITED";
        assertEquals(-1, classification.classifyNextBatch());                       // 1. hata: 60 sn
        assertEquals(GameType.UNKNOWN, typeOf("1"));
        assertEquals(-1, classification.classifyNextBatch()); assertEquals(1, provider.typeQueries.size(), "duraklatma sırasında yeniden sorulmaz");
        clock.advance(Duration.ofSeconds(61));
        assertEquals(-1, classification.classifyNextBatch()); assertEquals(2, provider.typeQueries.size());   // 2. hata: 4 x 60 sn
        clock.advance(Duration.ofSeconds(61));
        assertEquals(-1, classification.classifyNextBatch()); assertEquals(2, provider.typeQueries.size(), "bekleme arttı: 61 sn yetmez");
        provider.typeFailure = null; clock.advance(Duration.ofSeconds(200));
        assertEquals(1, classification.classifyNextBatch()); assertEquals(GameType.MAIN_GAME, typeOf("1"));
        // Başarıdan sonra hata sayacı sıfırlanır: yeni tek hata yine kısa bekletir.
        assertEquals(-1, classification.classifyNextBatch());                       // tarama biter, 6 saat bekler
        clock.advance(Duration.ofHours(6));
        provider.typeFailure = "PROVIDER_UNAVAILABLE";
        clock.advance(CatalogClassificationService.RECHECK_AFTER);
        assertEquals(-1, classification.classifyNextBatch());                       // sayaç sıfırlanmıştı: yine 60 sn
        clock.advance(Duration.ofSeconds(61)); provider.typeFailure = null; provider.typeEvidence.put("2", "dlc");
        assertEquals(1, classification.classifyNextBatch()); assertEquals(GameType.DLC, typeOf("2"));
    }
    @Test void bulkClassificationPausesForAnHourOnPermanentProviderErrors() {
        unknownSteamGames(1); provider.typeEvidence.put("1", "main_game"); provider.typeFailure = "PROVIDER_ACCESS_DENIED";
        assertEquals(-1, classification.classifyNextBatch());
        clock.advance(Duration.ofMinutes(59)); provider.typeFailure = null;
        assertEquals(-1, classification.classifyNextBatch()); assertEquals(1, provider.typeQueries.size());
        clock.advance(Duration.ofMinutes(2));
        assertEquals(1, classification.classifyNextBatch());
    }
    @Test void bulkClassificationWritesTheReleaseDateOnlyForClassifiedGamesWithoutLockOrExistingDate() {
        unknownSteamGames(5);
        for (String id : List.of("1", "2", "3", "5")) provider.typeEvidence.put(id, "main_game");
        provider.dateEvidence.put("1", LocalDate.of(2022, 2, 25));
        provider.dateEvidence.put("2", LocalDate.of(2020, 12, 10));
        provider.dateEvidence.put("3", LocalDate.of(2019, 1, 1));
        provider.dateEvidence.put("4", LocalDate.of(2018, 8, 8));      // tür kanıtı yok -> UNKNOWN kalır, tarih de yazılmaz
        provider.typeEvidence.put("5", "dlc");
        UUID second = refs.findByProviderAndExternalId("STEAM", "2").orElseThrow().getGame().getId();
        UUID third = refs.findByProviderAndExternalId("STEAM", "3").orElseThrow().getGame().getId();
        jdbc.update("update games set release_date='2001-01-01' where id=?", second);                       // mevcut tarih ezilmez
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at) values (?,?,'RELEASE_DATE',?,now())", UUID.randomUUID(), third, UUID.randomUUID());
        assertEquals(4, classification.classifyNextBatch());
        assertEquals(LocalDate.of(2022, 2, 25), games.findById(refs.findByProviderAndExternalId("STEAM", "1").orElseThrow().getGame().getId()).orElseThrow().getReleaseDate());
        assertEquals(LocalDate.of(2001, 1, 1), games.findById(second).orElseThrow().getReleaseDate());
        assertNull(games.findById(third).orElseThrow().getReleaseDate());
        assertEquals(GameType.MAIN_GAME, typeOf("3"));
        assertNull(games.findById(refs.findByProviderAndExternalId("STEAM", "4").orElseThrow().getGame().getId()).orElseThrow().getReleaseDate());
        assertEquals(GameType.UNKNOWN, typeOf("4"));
        assertNull(games.findById(refs.findByProviderAndExternalId("STEAM", "5").orElseThrow().getGame().getId()).orElseThrow().getReleaseDate());
        assertEquals(provider.detailSource(), jdbc.queryForObject("select source from game_metadata_provenance where field='RELEASE_DATE' and game_id=?", String.class,
                refs.findByProviderAndExternalId("STEAM", "1").orElseThrow().getGame().getId()));
    }
    private java.util.List<String> termCodes(String steamId, com.ltz.game.domain.enums.TermKind kind) {
        return jdbc.queryForList("select t.code from game_terms gt join catalog_terms t on t.id=gt.term_id where gt.game_id=? and t.kind=? order by t.code", String.class,
                refs.findByProviderAndExternalId("STEAM", steamId).orElseThrow().getGame().getId(), kind.name());
    }
    @Test void bulkClassificationAttachesGenresAndPlatformsOnlyToClassifiedGamesWithoutExistingTermsAndTheyMatchTheCatalogFilters() throws Exception {
        unknownSteamGames(4);
        var rpg = new GameProvider.TermEvidence("role-playing-rpg", "Role-playing (RPG)");
        var win = new GameProvider.TermEvidence("win", "PC (Microsoft Windows)");
        for (String id : List.of("1", "2", "3")) provider.typeEvidence.put(id, "main_game");
        provider.genreEvidence.put("1", List.of(rpg)); provider.platformEvidence.put("1", List.of(win));
        provider.genreEvidence.put("2", List.of(rpg)); provider.platformEvidence.put("2", List.of(win));
        provider.genreEvidence.put("4", List.of(rpg));                       // tür kanıtı yok -> UNKNOWN kalır, terim yazılmaz
        jdbc.update("insert into catalog_terms(id,kind,code,name_tr,name_en,version) values (gen_random_uuid(),'GENRE','strategy','Strateji','Strategy',0)");
        UUID second = refs.findByProviderAndExternalId("STEAM", "2").orElseThrow().getGame().getId();
        jdbc.update("insert into game_terms(game_id,term_id) select ?, id from catalog_terms where code='strategy'", second);   // yönetici verisi: tür ezilmez
        assertEquals(3, classification.classifyNextBatch());
        assertEquals(List.of("role-playing-rpg"), termCodes("1", com.ltz.game.domain.enums.TermKind.GENRE));
        assertEquals(List.of("win"), termCodes("1", com.ltz.game.domain.enums.TermKind.PLATFORM));
        assertEquals(List.of("strategy"), termCodes("2", com.ltz.game.domain.enums.TermKind.GENRE), "mevcut tür varsa sağlayıcı verisi eklenmez");
        assertEquals(List.of("win"), termCodes("2", com.ltz.game.domain.enums.TermKind.PLATFORM), "platform boştu: sağlayıcıdan gelir");
        assertEquals(List.of(), termCodes("3", com.ltz.game.domain.enums.TermKind.GENRE));
        assertEquals(List.of(), termCodes("4", com.ltz.game.domain.enums.TermKind.GENRE));
        assertEquals("Role-playing (RPG)", jdbc.queryForObject("select name_en from catalog_terms where code='role-playing-rpg'", String.class));
        assertNull(jdbc.queryForObject("select name_tr from catalog_terms where code='role-playing-rpg'", String.class), "Türkçe ad uydurulmaz");
        assertEquals(1L, jdbc.queryForObject("select count(*) from catalog_terms where kind='GENRE' and code='role-playing-rpg'", Long.class), "terim paylaşılır, kopyalanmaz");
        UUID first = refs.findByProviderAndExternalId("STEAM", "1").orElseThrow().getGame().getId();
        assertEquals("STEAM", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='GENRES'", String.class, first));
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_metadata_provenance where game_id=? and field='GENRES'", Long.class, second), "yazılmayan alan için gözlem kaydı yok");
        // Seçilen terim kodları gerçek katalog filtreleriyle eşleşir (yalnız ACTIVE + MAIN_GAME).
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/game/games").param("genre", "role-playing-rpg"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/game/games").param("platform", "win"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(2));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/game/terms").param("kind", "PLATFORM"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].code").value("win"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].gameCount").value(2));
    }
    @Test void bulkClassificationIsIdempotentAndNeverReclassifiesOrMovesDates() {
        unknownSteamGames(2); provider.typeEvidence.put("1", "main_game"); provider.dateEvidence.put("1", LocalDate.of(2022, 2, 25));
        assertEquals(1, classification.classifyNextBatch());
        provider.typeEvidence.put("1", "dlc"); provider.dateEvidence.put("1", LocalDate.of(1999, 1, 1));
        classification.restartPassForTests();
        classification.classifyNextBatch();
        assertEquals(GameType.MAIN_GAME, typeOf("1"));
        assertEquals(LocalDate.of(2022, 2, 25), games.findById(refs.findByProviderAndExternalId("STEAM", "1").orElseThrow().getGame().getId()).orElseThrow().getReleaseDate());
        assertEquals(1L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='GAME_TYPE' and data_state='AVAILABLE'", Long.class));
    }
    @Test void theNewestAppIdsAreClassifiedFirstSoModernGamesDoNotWaitBehindOldOnes() {
        unknownSteamGames(CatalogClassificationService.BATCH + 50);
        for (int id = 1; id <= CatalogClassificationService.BATCH + 50; id++) provider.typeEvidence.put(Integer.toString(id), "main_game");
        classification.classifyNextBatch();
        assertEquals(CatalogClassificationService.BATCH, jdbc.queryForObject("select count(*) from games where game_type='MAIN_GAME'", Long.class).intValue());
        assertEquals(GameType.MAIN_GAME, typeOf("300")); assertEquals(GameType.MAIN_GAME, typeOf("51"));
        assertEquals(GameType.UNKNOWN, typeOf("50"), "en eski kayıtlar sona kalır");
    }
    @Test void theBacklogSwitchIsOnByDefaultAndWhenOffAHugeBacklogDoesNotRunNotEvenAPilotAfterARestart() {
        unknownSteamGames(CatalogClassificationService.BATCH * 2);
        for (int id = 1; id <= CatalogClassificationService.BATCH * 2; id++) provider.typeEvidence.put(Integer.toString(id), "main_game");
        classification.gateForTests(10, false);                                      // anahtar kapalı, eşik 10: 500 aday büyük birikimdir
        assertEquals(-1, classification.classifyNextBatch(), "anahtar kapalıyken büyük birikim çalışmaz");
        classification.gateForTests(10, false);                                      // yeniden başlatma benzetimi: kendiliğinden deneme grubu yok
        assertEquals(-1, classification.classifyNextBatch());
        assertTrue(provider.typeQueries.isEmpty(), "sağlayıcıya hiç istek gitmez");
        classification.backlogEnabledForTests(true);
        clock.advance(CatalogClassificationService.PASS_INTERVAL.plusMinutes(1));
        assertEquals(CatalogClassificationService.BATCH, classification.classifyNextBatch());
        assertEquals(CatalogClassificationService.BATCH, classification.classifyNextBatch());
        assertEquals(2 * CatalogClassificationService.BATCH, jdbc.queryForObject("select count(*) from games where game_type='MAIN_GAME'", Long.class).intValue());
    }
    @Test void aSmallCandidateSetIsProcessedAutomaticallyEvenWithTheSwitchOff() {
        unknownSteamGames(5);
        for (int id = 1; id <= 5; id++) provider.typeEvidence.put(Integer.toString(id), "main_game");
        classification.gateForTests(10, false);
        assertEquals(5, classification.classifyNextBatch());
    }
    @Test void everyBacklogRecordIsEvaluatedExactlyOncePerPassAndTheTwoEndsMeetWithoutRepeats() {
        int total = CatalogClassificationService.BATCH * 2 + 40;
        unknownSteamGames(total);
        for (int id = 1; id <= total; id += 2) provider.typeEvidence.put(Integer.toString(id), "main_game");   // yarısı kanıtlı
        int steps = 0;
        while (classification.classifyNextBatch() >= 0) steps++;
        assertEquals(3, steps, "540 aday: üç adım (azalan, artan, azalan)");
        List<String> asked = provider.typeQueries.stream().flatMap(List::stream).toList();
        assertEquals(total, asked.size());
        assertEquals(total, asked.stream().distinct().count(), "hiçbir kayıt aynı turda iki kez sorulmaz");
        assertEquals(total / 2, jdbc.queryForObject("select count(*) from games where game_type='MAIN_GAME'", Long.class).intValue());
        assertEquals(total / 2, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='GAME_TYPE' and data_state='UNKNOWN'", Long.class).intValue());
    }
    @Test void bothEndsAdvanceSoNewestRecordsAreFirstAndTheOldestStillProgress() {
        unknownSteamGames(CatalogClassificationService.BATCH * 2);
        for (int id = 1; id <= CatalogClassificationService.BATCH * 2; id++) provider.typeEvidence.put(Integer.toString(id), "main_game");
        classification.classifyNextBatch(); classification.classifyNextBatch();
        assertEquals(Integer.toString(CatalogClassificationService.BATCH * 2), provider.typeQueries.get(0).getFirst(), "ilk adım en yeni uçtan");
        assertEquals("1", provider.typeQueries.get(1).getFirst(), "ikinci adım en eski uçtan");
    }
    @Test void aProviderFailureNeverWritesANoEvidenceMarkerSoNoRecordIsHeldBackByTheWaitingPolicy() {
        unknownSteamGames(3); provider.typeEvidence.put("2", "main_game"); provider.typeFailure = "RATE_LIMITED";
        assertEquals(-1, classification.classifyNextBatch());
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='GAME_TYPE'", Long.class), "hata, 'eşleşme yok' gözlemi değildir");
        provider.typeFailure = null; clock.advance(Duration.ofMinutes(2));
        assertEquals(1, classification.classifyNextBatch(), "hata geçince tüm kayıtlar hâlâ adaydır");
        assertEquals(GameType.MAIN_GAME, typeOf("2"));
    }
    @Test void newRecordsWithoutEvidenceAreRecheckedAfterADayAndOldOnesAfterAWeek() {
        unknownSteamGames(2);
        jdbc.update("update games set created_at = now() - interval '90 days' where title = 'G1'");      // eski kayıt (G1), yeni kayıt (G2)
        assertEquals(0, classification.classifyNextBatch());                                           // ikisi de kanıtsız: işaretlenir
        classification.restartPassForTests();
        clock.advance(Duration.ofHours(25));
        provider.typeEvidence.put("1", "main_game"); provider.typeEvidence.put("2", "main_game");
        assertEquals(1, classification.classifyNextBatch(), "1 gün sonra yalnız yeni kayıt (G2) yeniden sorulur");
        assertEquals(GameType.MAIN_GAME, typeOf("2")); assertEquals(GameType.UNKNOWN, typeOf("1"));
        classification.restartPassForTests();
        clock.advance(Duration.ofDays(7));
        assertEquals(1, classification.classifyNextBatch(), "7 gün sonra eski kayıt (G1) da yeniden sorulur");
        assertEquals(GameType.MAIN_GAME, typeOf("1"));
    }
    @Test void restartingTheProcessResumesWhereItStoppedBecauseProcessedRecordsAreNoLongerCandidates() {
        unknownSteamGames(CatalogClassificationService.BATCH * 2);
        for (int id = 1; id <= CatalogClassificationService.BATCH * 2; id++) provider.typeEvidence.put(Integer.toString(id), "main_game");
        classification.classifyNextBatch();                                                            // yarım kalan iş (yalnız 250)
        classification.restartPassForTests();                                                          // süreç yeniden başladı: bellekteki imleçler gitti
        provider.typeQueries.clear();
        assertEquals(CatalogClassificationService.BATCH, classification.classifyNextBatch());
        assertEquals(CatalogClassificationService.BATCH, provider.typeQueries.getFirst().size());
        assertTrue(provider.typeQueries.getFirst().stream().noneMatch(id -> Integer.parseInt(id) > CatalogClassificationService.BATCH), "işlenmiş kayıtlar yeniden sorulmaz");
        assertEquals(2 * CatalogClassificationService.BATCH, jdbc.queryForObject("select count(*) from games where game_type='MAIN_GAME'", Long.class).intValue());
    }
    @Test void metadataBackfillFillsOnlyEmptyFieldsOfAlreadyClassifiedGamesAndNeverChangesTheirType() {
        unknownSteamGames(4);
        jdbc.update("update games set game_type='MAIN_GAME'");
        jdbc.update("update games set game_type='DLC' where title='G2'");
        jdbc.update("update games set game_type='UNKNOWN' where title='G4'");                            // sınıflanmamış kayıt zenginleştirilmez
        var rpg = new GameProvider.TermEvidence("role-playing-rpg", "Role-playing (RPG)");
        for (String id : List.of("1", "2", "3", "4")) {
            provider.genreEvidence.put(id, List.of(rpg)); provider.platformEvidence.put(id, List.of(new GameProvider.TermEvidence("win", "PC")));
            provider.categoryEvidence.put(id, List.of(new GameProvider.TermEvidence("single-player", "Single player")));
            provider.tagEvidence.put(id, List.of(new GameProvider.TermEvidence("fantasy", "Fantasy")));
            provider.dateEvidence.put(id, LocalDate.of(2022, 2, 25)); provider.typeEvidence.put(id, "dlc");          // tür kanıtı DLC der: ama tür değişmez
        }
        UUID third = refs.findByProviderAndExternalId("STEAM", "3").orElseThrow().getGame().getId();
        jdbc.update("update games set release_date='2001-01-01' where id=?", third);                                             // mevcut tarih ezilmez
        jdbc.update("insert into catalog_terms(id,kind,code,name_tr,name_en,version) values (gen_random_uuid(),'GENRE','strategy','Strateji','Strategy',0)");
        jdbc.update("insert into game_terms(game_id,term_id) select ?, id from catalog_terms where code='strategy'", third);   // yönetici verisi: tür ezilmez
        assertEquals(3, classification.enrichNextBatch());
        assertEquals(GameType.MAIN_GAME, typeOf("1")); assertEquals(GameType.DLC, typeOf("2")); assertEquals(GameType.MAIN_GAME, typeOf("3"));
        assertEquals(List.of("role-playing-rpg"), termCodes("1", com.ltz.game.domain.enums.TermKind.GENRE));
        assertEquals(List.of("win"), termCodes("1", com.ltz.game.domain.enums.TermKind.PLATFORM));
        assertEquals(List.of("single-player"), termCodes("1", com.ltz.game.domain.enums.TermKind.CATEGORY), "oyun modu kategoriye yazılır");
        assertEquals(List.of("fantasy"), termCodes("1", com.ltz.game.domain.enums.TermKind.TAG), "tema etikete yazılır");
        assertEquals(List.of("strategy"), termCodes("3", com.ltz.game.domain.enums.TermKind.GENRE), "mevcut tür varsa ezilmez");
        assertEquals(List.of("win"), termCodes("3", com.ltz.game.domain.enums.TermKind.PLATFORM));
        assertEquals(LocalDate.of(2001, 1, 1), games.findById(third).orElseThrow().getReleaseDate());
        assertEquals(LocalDate.of(2022, 2, 25), games.findById(refs.findByProviderAndExternalId("STEAM", "1").orElseThrow().getGame().getId()).orElseThrow().getReleaseDate());
        assertEquals(List.of(), termCodes("4", com.ltz.game.domain.enums.TermKind.GENRE));
        assertEquals(3L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='IGDB_METADATA' and data_state='AVAILABLE'", Long.class));
        // Bir kez denenen kayıt yeniden sorulmaz (idempotent); tur biter.
        int queries = provider.typeQueries.size();
        assertEquals(-1, classification.enrichNextBatch()); assertEquals(queries, provider.typeQueries.size());
    }
    @Test void metadataBackfillMarksGamesWithoutEvidenceAsTriedAndRespectsManualDateLocks() {
        unknownSteamGames(2);
        jdbc.update("update games set game_type='MAIN_GAME'");
        provider.dateEvidence.put("1", LocalDate.of(2020, 5, 5));
        UUID first = refs.findByProviderAndExternalId("STEAM", "1").orElseThrow().getGame().getId();
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at) values (?,?,'RELEASE_DATE',?,now())", UUID.randomUUID(), first, UUID.randomUUID());
        assertEquals(1, classification.enrichNextBatch());
        assertNull(games.findById(first).orElseThrow().getReleaseDate(), "manuel tarih kilidi korunur");
        assertEquals(1L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='IGDB_METADATA' and data_state='EMPTY'", Long.class), "kanıtsız kayıt denendi sayılır");
        assertEquals(-1, classification.enrichNextBatch());
    }
    @Test void metadataBackfillPausesOnProviderFailureWithoutMarkingAnything() {
        unknownSteamGames(2); jdbc.update("update games set game_type='MAIN_GAME'");
        provider.typeFailure = "PROVIDER_UNAVAILABLE";
        assertEquals(-1, classification.enrichNextBatch());
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='IGDB_METADATA'", Long.class));
        provider.typeFailure = null; provider.dateEvidence.put("1", LocalDate.of(2020, 1, 1)); clock.advance(Duration.ofMinutes(2));
        assertEquals(1, classification.enrichNextBatch());
    }
    @Test void evidenceFreeAndNonTargetRecordsStayUnknownAreMarkedAndOnlyRecheckedAfterTheWaitingPeriod() {
        unknownSteamGames(3);
        provider.dateEvidence.put("1", LocalDate.of(2020, 1, 1));               // IGDB'de var ama tür hedef dışı (mod/port...): evidence türü null
        provider.genreEvidence.put("1", List.of(new GameProvider.TermEvidence("indie", "Indie")));
        provider.typeEvidence.put("3", "main_game");
        assertEquals(1, classification.classifyNextBatch());
        assertEquals(GameType.UNKNOWN, typeOf("1")); assertEquals(GameType.UNKNOWN, typeOf("2")); assertEquals(GameType.MAIN_GAME, typeOf("3"));
        assertNull(games.findById(refs.findByProviderAndExternalId("STEAM", "1").orElseThrow().getGame().getId()).orElseThrow().getReleaseDate(), "tür kanıtı yoksa tarih de yazılmaz");
        assertEquals(List.of(), termCodes("1", com.ltz.game.domain.enums.TermKind.GENRE));
        assertEquals(2L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='GAME_TYPE' and data_state='UNKNOWN'", Long.class));
        assertEquals(-1, classification.classifyNextBatch());
        int queries = provider.typeQueries.size();
        clock.advance(CatalogClassificationService.PASS_INTERVAL.plusMinutes(1));
        assertEquals(-1, classification.classifyNextBatch()); assertEquals(queries, provider.typeQueries.size(), "bekleme süresi dolmadan yeniden sorulmaz");
        provider.typeEvidence.put("2", "dlc");
        clock.advance(CatalogClassificationService.RECHECK_AFTER.plusHours(1));
        assertEquals(1, classification.classifyNextBatch(), "bekleme bitince kanıt gelen kayıt sınıflanır");
        assertEquals(GameType.DLC, typeOf("2"));
    }
    @Test void aSteamModificationClearsTheNoEvidenceMarkerSoTheRecordIsRecheckedEarly() {
        provider.pageAt(10, "Maybe", Instant.EPOCH, false); imports.synchronizeNextPage();
        assertEquals(0, classification.classifyNextBatch(), "kanıtsız kayıt sınıflanmaz");
        assertEquals(1L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='GAME_TYPE' and data_state='UNKNOWN'", Long.class));
        clock.advance(Duration.ofHours(24));
        provider.pageAt(10, "Maybe renamed", Instant.parse("2026-10-21T12:00:00Z"), false); imports.synchronizeNextPage();
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='GAME_TYPE'", Long.class), "değişen kayıt yeniden sınıflandırmaya uygun");
        assertEquals("Maybe renamed", games.findById(refs.findByProviderAndExternalId("STEAM", "10").orElseThrow().getGame().getId()).orElseThrow().getTitle());
    }
    @Test void dailyRunStartsOncePerIstanbulWindowReadsTheCheckpointAndCatchesUpAfterDowntime() {
        assertEquals(Instant.parse("2026-10-21T00:00:00Z"), CatalogImportService.latestSyncWindow(Instant.parse("2026-10-21T00:00:00Z")), "03:00 Europe/Istanbul = 00:00Z");
        assertEquals(Instant.parse("2026-10-21T00:00:00Z"), CatalogImportService.latestSyncWindow(Instant.parse("2026-10-21T23:59:59Z")), "02:59:59 yerel: bir önceki pencere");
        assertEquals(Instant.parse("2026-10-22T00:00:00Z"), CatalogImportService.latestSyncWindow(Instant.parse("2026-10-22T00:00:00Z")));
        provider.page(10, false); imports.synchronizeNextPage();
        Instant firstSuccess = imports.status().lastSuccessfulAt();
        int queries = provider.queries.size();
        clock.advance(Duration.ofHours(23).plusMinutes(59)); imports.synchronizeNextPage();
        assertEquals(queries, provider.queries.size(), "aynı pencerede ikinci tur başlamaz");
        clock.advance(Duration.ofMinutes(1)); provider.page(10, false); imports.synchronizeNextPage();
        assertEquals(queries + 1, provider.queries.size());
        assertEquals(CatalogRunMode.INCREMENTAL, imports.status().runMode());
        assertEquals(firstSuccess.minusSeconds(1), provider.queries.getLast().modifiedSince(), "if_modified_since son başarılı checkpoint'ten gelir");
        assertEquals(0, provider.queries.getLast().afterAppId());
        Instant secondSuccess = imports.status().lastSuccessfulAt();
        assertTrue(secondSuccess.isAfter(firstSuccess));
        imports.synchronizeNextPage(); assertEquals(queries + 1, provider.queries.size());
        clock.advance(Duration.ofDays(5)); provider.page(10, false); imports.synchronizeNextPage();   // uzun kesinti: tek tur, birikmiş gün sayısı kadar değil
        assertEquals(queries + 2, provider.queries.size());
        assertEquals(secondSuccess.minusSeconds(1), provider.queries.getLast().modifiedSince());
        imports.synchronizeNextPage(); assertEquals(queries + 2, provider.queries.size());
    }
    @Test void aFailedDailyRunKeepsTheCheckpointNeverReportsSuccessAndRestartsInTheNextWindowFromTheSameWatermark() {
        provider.page(10, false); imports.synchronizeNextPage();
        Instant checkpoint = imports.status().lastSuccessfulAt();
        clock.advance(Duration.ofHours(24)); provider.fail("INVALID_PROVIDER_RESPONSE"); imports.synchronizeNextPage();
        var failed = imports.status();
        assertEquals(com.ltz.game.domain.enums.CatalogSyncStatus.FAILED, failed.syncStatus());
        assertEquals(checkpoint, failed.lastSuccessfulAt(), "başarısız tur checkpoint'i ilerletmez");
        int queries = provider.queries.size();
        clock.advance(Duration.ofHours(5)); imports.synchronizeNextPage();
        assertEquals(queries, provider.queries.size(), "aynı pencerede kalıcı hata yeniden denenmez");
        clock.advance(Duration.ofHours(20)); provider.page(10, false); imports.synchronizeNextPage();
        assertEquals(queries + 1, provider.queries.size());
        assertEquals(checkpoint.minusSeconds(1), provider.queries.getLast().modifiedSince(), "aynı watermark: kaçırılan güncellemeler alınır");
        assertEquals(com.ltz.game.domain.enums.CatalogSyncStatus.COMPLETE, imports.status().syncStatus());
        assertTrue(imports.status().lastSuccessfulAt().isAfter(checkpoint));
        assertEquals(1, games.count());
    }
    @Test void unchangedSteamRecordsAreSkippedWhileModifiedOnesUpdateWithoutDuplicates() {
        provider.pageAt(10, "Old name", Instant.EPOCH, false); imports.synchronizeNextPage();
        UUID id = refs.findByProviderAndExternalId("STEAM", "10").orElseThrow().getGame().getId();
        Instant updatedBefore = jdbc.queryForObject("select updated_at from games where id=?", java.sql.Timestamp.class, id).toInstant();
        clock.advance(Duration.ofHours(24));
        provider.pageAt(10, "Renamed but not modified", Instant.EPOCH, false); imports.synchronizeNextPage();
        assertEquals("Old name", games.findById(id).orElseThrow().getTitle(), "last_modified senkrondan sonra değilse yazma yapılmaz");
        assertEquals(updatedBefore, jdbc.queryForObject("select updated_at from games where id=?", java.sql.Timestamp.class, id).toInstant());
        clock.advance(Duration.ofHours(24));
        provider.pageAt(10, "Renamed", Instant.now().plusSeconds(3600), false); imports.synchronizeNextPage();
        assertEquals("Renamed", games.findById(id).orElseThrow().getTitle());
        assertEquals(id, refs.findByProviderAndExternalId("STEAM", "10").orElseThrow().getGame().getId(), "Game kimliği korunur");
        assertEquals(1, games.count()); assertEquals(1, refs.count());
    }
    @Test void onlyActiveMainGamesAreInThePublicCatalogueDlcAndUnclassifiedRecordsAreNot() throws Exception {
        unknownSteamGames(3);
        jdbc.update("update games set game_type='MAIN_GAME' where title='G1'");
        jdbc.update("update games set game_type='DLC' where title='G2'");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/game/games"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].game.title").value("G1"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/game/games").param("q", "G2"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(0));
    }
    @Test void bulkClassificationNeedsTheItemTypeCapability() {
        unknownSteamGames(1); provider.typeEvidence.put("1", "main_game"); provider.supported = false;
        assertEquals(-1, classification.classifyNextBatch());
        assertTrue(provider.typeQueries.isEmpty()); assertEquals(GameType.UNKNOWN, typeOf("1"));
    }
    @Test void failedDetailAttemptsLeaveTheGameTypeAndOutOfTheBootstrapQueue() {
        UUID id = indexed(); jdbc.update("update games set game_type='MAIN_GAME' where id=?", id);
        provider.details.add(() -> { throw new ProviderException("PROVIDER_ACCESS_DENIED"); });
        assertEquals("FAILED", refresh.refresh(id));
        assertEquals(GameType.MAIN_GAME, games.findById(id).orElseThrow().getGameType());
        assertTrue(refresh.detailBootstrapCandidates(5).isEmpty());
        assertEquals("STOPPED", refresh.refresh(id));
    }
    @Test void manualTypeLockAndExistingManualClassificationSurviveProviderEvidence() {
        UUID id = indexed();
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at) values (?,?,'GAME_TYPE',?,now())", UUID.randomUUID(), id, UUID.randomUUID());
        jdbc.update("update games set game_type='SOFTWARE' where id=?", id);
        provider.details.add(() -> typed("main_game"));
        assertEquals("REFRESHED", refresh.refresh(id));
        assertEquals(GameType.SOFTWARE, games.findById(id).orElseThrow().getGameType());
    }
    @Test void nameLookingLikeAMainGameDoesNotClassifyWithoutEvidence() {
        provider.pages.add(() -> new GameProvider.CatalogPage(List.of(new GameProvider.CatalogItem("7", "Totally A Game", Instant.EPOCH)), 7, false));
        imports.synchronizeNextPage();
        assertEquals(0L, jdbc.queryForObject("select count(*) from games where game_type='MAIN_GAME'", Long.class));
    }
}
