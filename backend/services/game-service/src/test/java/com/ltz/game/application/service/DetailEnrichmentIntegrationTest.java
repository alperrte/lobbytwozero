package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.port.StoreDetailSource;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toplu detay (IGDB) ve Steam mağaza tamamlama: gerçek PostgreSQL üzerinde. Kapak önceliği (yönetici &gt; Steam &gt; IGDB), yalnız boş alanların
 * doldurulması, "bulunamadı" işareti, hata duraklaması ve tekrar sorulmama doğrulanır.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
@Import(DetailEnrichmentIntegrationTest.Fakes.class)
class DetailEnrichmentIntegrationTest extends PostgresTestSupport {
    static final String HASH = "ce4d5e53b36cb9d3c4309d1df72bf8663bbbc7ef";
    static final String IGDB_COVER = "https://images.igdb.com/igdb/image/upload/t_cover_big/co4sbw.jpg";
    static final String STEAM_COVER = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/%s/" + HASH + "/header.jpg";
    static final String ADMIN_COVER = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/9/header.jpg";

    @TestConfiguration static class Fakes {
        @Bean @Primary DetailProvider detailProvider() { return new DetailProvider(); }
        @Bean @Primary FakeStore fakeStore() { return new FakeStore(); }
        @Bean @Primary MutableClock enrichmentClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class DetailProvider implements GameProvider {
        final Map<String, Details> found = new HashMap<>();
        final List<List<String>> queries = new CopyOnWriteArrayList<>();
        String failure;
        @Override public String provider() { return "STEAM"; }
        @Override public String detailSource() { return "IGDB"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG, Capability.DETAILS, Capability.ITEM_TYPE); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, Details> bulkDetails(java.util.Collection<String> ids) {
            queries.add(List.copyOf(ids));
            if (failure != null) throw new ProviderException(failure);
            Map<String, Details> result = new HashMap<>();
            ids.forEach(id -> { if (found.containsKey(id)) result.put(id, found.get(id)); });
            return result;
        }
    }

    static class FakeStore implements StoreDetailSource {
        final Map<String, Optional<StoreDetails>> data = new HashMap<>();
        final List<String> calls = new CopyOnWriteArrayList<>();
        String failure;
        @Override public Optional<StoreDetails> details(String appId) {
            calls.add(appId);
            if (failure != null) throw new ProviderException(failure);
            return data.getOrDefault(appId, Optional.empty());
        }
    }

    @Autowired DetailProvider provider;
    @Autowired FakeStore store;
    @Autowired MutableClock clock;
    @Autowired GameDetailBulkService bulk;
    @Autowired SteamStoreEnrichmentService enrichment;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.found.clear(); provider.queries.clear(); provider.failure = null;
        store.data.clear(); store.calls.clear(); store.failure = null;
        bulk.resetForTests(); enrichment.resetForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    private UUID mainGame(String appId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) "
                + "values (?,?,?,'EXTERNAL','MAIN_GAME','INDEX_ONLY',now(),now())", id, "G" + appId, "g" + appId);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        return id;
    }

    private void cover(UUID game, String url, String provider) {
        jdbc.update("insert into game_media(id,game_id,type,url,provider,sort_order,version) values (?,?,'COVER',?,?,0,0)", UUID.randomUUID(), game, url, provider);
    }

    private String coverOf(UUID game) {
        var rows = jdbc.queryForList("select url from game_media where game_id=? and type='COVER'", String.class, game);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String coverProvider(UUID game) { return jdbc.queryForObject("select provider from game_media where game_id=? and type='COVER'", String.class, game); }

    private static GameProvider.Details igdbDetails(String developer) {
        return new GameProvider.Details("Summary", java.time.LocalDate.of(2022, 10, 28), developer == null ? Set.of() : Set.of(developer), Set.of("Publisher"), "main_game",
                List.of(new GameProvider.LanguageSupport("en-us", LanguageSupportStatus.SUPPORTED, LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.UNKNOWN)), IGDB_COVER);
    }

    private static StoreDetailSource.StoreDetails steamDetails(String app, Set<String> developers, Set<String> publishers, List<GameProvider.LanguageSupport> languages) {
        return new StoreDetailSource.StoreDetails("game", STEAM_COVER.formatted(app), developers, publishers, languages, null);
    }

    @Test void bulkDetailsFillTheBatchAtOnceKeepAdminCoversAndMarkMissingGamesNotFound() {
        UUID found = mainGame("1"), missing = mainGame("2"), adminCover = mainGame("3");
        cover(adminCover, ADMIN_COVER, "ADMIN");
        provider.found.put("1", igdbDetails("Dev"));
        provider.found.put("3", igdbDetails("Dev3"));

        assertEquals(3, bulk.bootstrapNextBatch());

        assertEquals(List.of(List.of("1", "2", "3")), provider.queries.stream().map(q -> q.stream().sorted().toList()).toList(), "tek toplu istek");
        assertEquals(IGDB_COVER, coverOf(found));
        assertEquals("IGDB", coverProvider(found));
        assertEquals(Set.of("Dev"), Set.copyOf(jdbc.queryForList("select name from game_developers where game_id=?", String.class, found)));
        assertEquals("AVAILABLE", jdbc.queryForObject("select language_data_state from games where id=?", String.class, found));
        assertEquals("PARTIAL", jdbc.queryForObject("select metadata_completeness from games where id=?", String.class, found));
        assertEquals(ADMIN_COVER, coverOf(adminCover), "yönetici kapağı ezilmez");
        assertEquals("ADMIN", coverProvider(adminCover));
        assertEquals("PROVIDER_ITEM_NOT_FOUND", jdbc.queryForObject("select details_last_error_code from games where id=?", String.class, missing));
        assertEquals(1, jdbc.queryForObject("select details_failure_count from games where id=?", Integer.class, missing));
        assertNull(coverOf(missing));
        assertEquals("IGDB", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='COVER_URL'", String.class, found));
        assertEquals(-1, bulk.bootstrapNextBatch(), "denenen kayıt yeniden aday olmaz");
        assertEquals(1, provider.queries.size());
    }

    @Test void bulkProviderFailurePausesWritesNothingAndKeepsTheGamesAsCandidates() {
        UUID game = mainGame("1");
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, bulk.bootstrapNextBatch());
        assertNull(jdbc.queryForObject("select details_last_error_code from games where id=?", String.class, game));
        provider.failure = null;
        provider.found.put("1", igdbDetails("Dev"));
        assertEquals(-1, bulk.bootstrapNextBatch(), "duraklatma süresi dolmadan denenmez");
        clock.advance(Duration.ofHours(2));
        assertEquals(1, bulk.bootstrapNextBatch());
        assertEquals(IGDB_COVER, coverOf(game));
    }

    @Test void storeEnrichmentFillsOnlyEmptyFieldsUpgradesIgdbCoverToSteamAndNeverTouchesAdminData() {
        UUID fromIgdb = mainGame("1"), notInIgdb = mainGame("2"), admin = mainGame("3");
        cover(admin, ADMIN_COVER, "ADMIN");
        provider.found.put("1", igdbDetails("Dev"));          // geliştirici var, yayıncı var, dil var, kapak IGDB
        provider.found.put("3", igdbDetails(null));           // geliştirici yok
        bulk.bootstrapNextBatch();
        var english = new GameProvider.LanguageSupport("en-us", LanguageSupportStatus.SUPPORTED, LanguageSupportStatus.SUPPORTED, LanguageSupportStatus.UNKNOWN);
        var turkish = new GameProvider.LanguageSupport("tr-tr", LanguageSupportStatus.SUPPORTED, LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.UNKNOWN);
        store.data.put("1", Optional.of(steamDetails("1", Set.of("Other Dev"), Set.of("Other Pub"), List.of(english, turkish))));
        store.data.put("2", Optional.of(steamDetails("2", Set.of("Infinity Ward", "Treyarch"), Set.of("Activision"), List.of(english, turkish))));
        store.data.put("3", Optional.of(steamDetails("3", Set.of("Steam Dev"), Set.of("Steam Pub"), List.of(english))));

        for (int i = 0; i < 3; i++) assertEquals(1, enrichment.enrichNext());

        // 1: IGDB verisi korunur, yalnız kapak Steam'e yükselir
        assertEquals(STEAM_COVER.formatted("1"), coverOf(fromIgdb));
        assertEquals("STEAM", coverProvider(fromIgdb));
        assertEquals(Set.of("Dev"), Set.copyOf(jdbc.queryForList("select name from game_developers where game_id=?", String.class, fromIgdb)), "dolu geliştirici ezilmez");
        assertEquals(1L, jdbc.queryForObject("select count(*) from game_language_supports where game_id=?", Long.class, fromIgdb), "dolu dil verisi ezilmez");
        // 2: IGDB'de hiç yok -> her şey Steam'den
        assertEquals(STEAM_COVER.formatted("2"), coverOf(notInIgdb));
        assertEquals(Set.of("Infinity Ward", "Treyarch"), Set.copyOf(jdbc.queryForList("select name from game_developers where game_id=?", String.class, notInIgdb)));
        assertEquals(Set.of("Activision"), Set.copyOf(jdbc.queryForList("select name from game_publishers where game_id=?", String.class, notInIgdb)));
        assertEquals(Set.of("en-us", "tr-tr"), Set.copyOf(jdbc.queryForList("select language_code from game_languages where game_id=?", String.class, notInIgdb)));
        assertEquals("AVAILABLE", jdbc.queryForObject("select language_data_state from games where id=?", String.class, notInIgdb));
        assertEquals("STEAM", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='DEVELOPERS'", String.class, notInIgdb));
        // 3: yönetici kapağı korunur, boş geliştirici dolar
        assertEquals(ADMIN_COVER, coverOf(admin));
        assertEquals(Set.of("Steam Dev"), Set.copyOf(jdbc.queryForList("select name from game_developers where game_id=?", String.class, admin)));
        assertEquals(3L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='STEAM_STORE'", Long.class));
    }

    @Test void gamesWithNoStoreDataAreMarkedNotAskedAgainAndProviderErrorsPauseWithoutAMarker() {
        UUID game = mainGame("1");
        provider.found.put("1", igdbDetails(null));
        bulk.bootstrapNextBatch();
        store.failure = "RATE_LIMITED";
        assertEquals(-1, enrichment.enrichNext());
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='STEAM_STORE'", Long.class));
        store.failure = null;
        clock.advance(Duration.ofHours(2));
        assertEquals(1, enrichment.enrichNext());   // mağazada veri yok
        assertEquals("UNKNOWN", jdbc.queryForObject("select data_state from game_metadata_provenance where game_id=? and field='STEAM_STORE'", String.class, game));
        assertEquals(-1, enrichment.enrichNext(), "işaretli kayıt 30 gün aday olmaz");
        assertEquals(2, store.calls.size());
        clock.advance(SteamStoreEnrichmentService.RECHECK.plusHours(3));
        assertEquals(1, enrichment.enrichNext(), "30 gün sonra yeniden bakılır");
    }

    @Test void gamesNotYetTriedByIgdbAreLeftToTheBulkPathFirst() {
        mainGame("1");
        assertEquals(-1, enrichment.enrichNext());
        assertTrue(store.calls.isEmpty());
    }
}
