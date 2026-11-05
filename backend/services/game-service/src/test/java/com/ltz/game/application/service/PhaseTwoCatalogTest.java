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
        @Override public String detailSource() { return source == null ? provider() : source; }
        void detailWith(GameProvider.LanguageSupport... languages) {
            details.add(() -> new Details("Provider description", LocalDate.of(2020, 1, 1), Set.of(), Set.of(), null, List.of(languages)));
        }
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return supported ? Set.of(Capability.CATALOG, Capability.INCREMENTAL_CATALOG, Capability.DETAILS, Capability.ITEM_TYPE) : Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { queries.add(query); return Objects.requireNonNull(pages.poll()).get(); }
        @Override public Details details(String id) { detailCalls++; return Objects.requireNonNull(details.poll()).get(); }
        void page(long id, boolean more) { pages.add(() -> new CatalogPage(List.of(new CatalogItem("" + id, "Example " + id, Instant.EPOCH)), id, more)); }
        void fail(String code) { pages.add(() -> { throw new ProviderException(code); }); }
        void detail() { details.add(() -> new Details("Local snapshot", LocalDate.of(2020, 1, 1), Set.of("Developer"), Set.of("Publisher"), "game")); }
    }
    @Autowired FakeProvider provider;
    @Autowired MutableClock clock;
    @Autowired CatalogImportService imports;
    @Autowired GameDetailRefreshService refresh;
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
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
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
        clock.advance(Duration.ofHours(6)); provider.page(20, false); imports.synchronizeNextPage();
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
}
