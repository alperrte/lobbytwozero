package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * DLC → ana oyun bağlama: IGDB birincil, Steam (toplu mağaza çağrısı) yalnız boşluk/belirsizlik/örneklem için; IGDB kapalıyken doğrudan Steam.
 * Çelişki ilişkiyi boş bırakır; UNKNOWN veya katalogda olmayan ana oyuna zorla bağlanmaz; gereksiz Steam isteği atılmaz.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
@Import(DlcParentIntegrationTest.Fakes.class)
class DlcParentIntegrationTest extends PostgresTestSupport {
    @TestConfiguration static class Fakes {
        @Bean @Primary ParentProvider parentProvider() { return new ParentProvider(); }
        @Bean @Primary MutableClock parentClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class ParentProvider implements GameProvider {
        final Map<String, RelationEvidence> relation = new HashMap<>();
        final List<List<String>> queries = new CopyOnWriteArrayList<>();
        /** Steam mağaza yanıtı: DLC AppID → ana oyun AppID'si. */
        final Map<String, String> fullGame = new HashMap<>();
        final List<List<String>> steamQueries = new CopyOnWriteArrayList<>();
        boolean igdb = true;
        String failure;
        String steamFailure;
        @Override public String provider() { return "STEAM"; }
        @Override public String detailSource() { return "IGDB"; }
        @Override public Set<Capability> capabilities() { return igdb ? Set.of(Capability.CATALOG, Capability.ITEM_TYPE) : Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, RelationEvidence> relationEvidence(java.util.Collection<String> ids) {
            queries.add(List.copyOf(ids));
            if (failure != null) throw new ProviderException(failure);
            Map<String, RelationEvidence> found = new HashMap<>();
            ids.forEach(id -> { if (relation.containsKey(id)) found.put(id, relation.get(id)); });
            return found;
        }
        @Override public Map<String, StoreType> storeTypes(java.util.Collection<String> ids) {
            steamQueries.add(List.copyOf(ids));
            if (steamFailure != null) throw new ProviderException(steamFailure);
            Map<String, StoreType> found = new HashMap<>();
            ids.forEach(id -> found.put(id, new StoreType(4, true, null, fullGame.get(id), Set.of(), Set.of(), Set.of())));
            return found;
        }
    }

    @Autowired ParentProvider provider;
    @Autowired MutableClock clock;
    @Autowired DlcParentService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.relation.clear(); provider.queries.clear(); provider.failure = null;
        provider.fullGame.clear(); provider.steamQueries.clear(); provider.steamFailure = null; provider.igdb = true;
        service.resetForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    private UUID game(String appId, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,?,?,'EXTERNAL',?,now(),now())", id, "G" + appId, "g" + appId, type);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        return id;
    }

    private UUID parentOf(UUID id) { return jdbc.queryForObject("select parent_game_id from games where id=?", UUID.class, id); }

    private static GameProvider.RelationEvidence linked(String parent) { return new GameProvider.RelationEvidence(1, GameProvider.ParentState.LINKED, parent); }

    private static GameProvider.RelationEvidence state(GameProvider.ParentState state) { return new GameProvider.RelationEvidence(1, state, null); }

    private void fixture() {
        provider.relation.put("11", linked("100"));
        provider.relation.put("12", state(GameProvider.ParentState.NONE));
        provider.relation.put("25", linked("100"));          // örneklem: Steam başka ana oyun diyor -> çelişki
        provider.relation.put("50", linked("100"));          // örneklem: Steam aynı ana oyunu doğruluyor
        provider.relation.put("14", linked("300"));          // ana oyun UNKNOWN
        provider.relation.put("15", linked("999"));          // katalogda yok
        provider.relation.put("17", state(GameProvider.ParentState.UNMAPPED));
        provider.fullGame.put("12", "200");
        provider.fullGame.put("25", "200");
        provider.fullGame.put("50", "100");
        provider.fullGame.put("17", "200");
    }

    @Test void igdbIsPrimaryAndSteamIsOnlyUsedForGapsAmbiguitySamplesAndConflictsLeaveTheRelationEmpty() {
        UUID main100 = game("100", "MAIN_GAME"), main200 = game("200", "MAIN_GAME");
        game("300", "UNKNOWN");
        UUID igdbLinked = game("11", "DLC"), steamLinked = game("12", "DLC"), conflict = game("25", "DLC"), verified = game("50", "DLC");
        UUID unclassified = game("14", "DLC"), notInCatalog = game("15", "DLC"), noEvidence = game("16", "DLC"), unmapped = game("17", "DLC");
        fixture();

        assertEquals(8, service.reviewNext(), "IGDB partisi: 8 DLC");
        assertEquals(main100, parentOf(igdbLinked), "IGDB üst oyunu Steam AppID'si üzerinden katalogdaki oyuna çevrilir");
        assertNull(parentOf(unclassified), "UNKNOWN ana oyuna zorla bağlanmaz");
        assertNull(parentOf(notInCatalog), "katalogda olmayan ana oyuna bağlanmaz");
        assertEquals(5, service.reviewNext(), "Steam: örneklemler ve boşluklar tek toplu çağrıda");
        assertEquals(-1, service.reviewNext(), "hepsi incelendi");

        assertEquals(main200, parentOf(steamLinked), "IGDB üst oyun bildirmediğinde Steam fullgame kullanılır");
        assertEquals(main200, parentOf(unmapped), "IGDB üst oyunu katalogla eşlenemediğinde Steam kullanılır");
        assertEquals(main100, parentOf(verified), "örneklem Steam ile doğrulandı");
        assertNull(parentOf(conflict), "IGDB ve Steam çelişirse ilişki boş kalır, rastgele seçilmez");
        assertNull(parentOf(noEvidence));
        assertEquals(1, provider.steamQueries.size(), "tek toplu Steam çağrısı");
        assertEquals(List.of("12", "16", "17", "25", "50"), provider.steamQueries.getFirst().stream().sorted().toList(), "gereksiz Steam isteği yok: 11, 14 ve 15 için Steam'e sorulmadı");
        assertEquals(Map.of(DlcParentService.Outcome.LINKED, 2L, DlcParentService.Outcome.LINKED_VIA_STEAM, 2L, DlcParentService.Outcome.CONFLICT, 1L,
                DlcParentService.Outcome.PARENT_NOT_CLASSIFIED, 1L, DlcParentService.Outcome.PARENT_NOT_IN_CATALOG, 1L,
                DlcParentService.Outcome.NO_PARENT_EVIDENCE, 1L), service.totals());
        assertEquals(11L, jdbc.queryForObject("select count(*) from games", Long.class), "oyun eklenmez/silinmez");
        assertEquals(11L, jdbc.queryForObject("select count(*) from game_external_refs", Long.class), "dış referanslar korunur");
        assertEquals(8L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='DLC_PARENT'", Long.class));
        assertEquals("INVALID", jdbc.queryForObject("select data_state from game_metadata_provenance where game_id=? and field='DLC_PARENT'", String.class, conflict));
        assertEquals(1, provider.queries.size(), "bağlananlar ve incelenenler yeniden sorulmaz");
        assertEquals(-1, service.reviewNext());
        assertEquals(1, provider.queries.size());
    }

    @Test void unlinkedRecordsAreReviewedAgainAfterTheRecheckWindowSoAParentClassifiedLaterIsLinked() {
        UUID parent = game("300", "UNKNOWN");
        UUID child = game("14", "DLC");
        provider.relation.put("14", linked("300"));
        assertEquals(1, service.reviewNext());
        assertNull(parentOf(child));
        clock.advance(DlcParentService.RECHECK.plusHours(1));
        jdbc.update("update games set game_type='MAIN_GAME' where id=?", parent);   // ana oyun sonradan normal akışla sınıflandı
        assertEquals(1, service.reviewNext());
        assertEquals(parent, parentOf(child));
    }

    @Test void providerFailureWritesNoMarkerAndPausesBeforeRetrying() {
        game("100", "MAIN_GAME");
        UUID child = game("11", "DLC");
        provider.relation.put("11", linked("100"));
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, service.reviewNext());
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='DLC_PARENT'", Long.class));
        provider.failure = null;
        assertEquals(-1, service.reviewNext(), "duraklatma süresi dolmadan denenmez");
        clock.advance(Duration.ofHours(2));
        assertEquals(1, service.reviewNext());
        assertEquals(1L, jdbc.queryForObject("select count(*) from games where parent_game_id is not null", Long.class));
        assertEquals(java.util.Optional.of(child).isPresent(), parentOf(child) != null);
    }

    @Test void withoutIgdbEveryCandidateIsAskedFromSteamInBatchesAndLinkedToTheParent() {
        provider.igdb = false;
        UUID main = game("100", "MAIN_GAME");
        UUID child = game("11", "DLC"), orphan = game("12", "DLC");
        provider.fullGame.put("11", "100");

        assertEquals(2, service.reviewNext(), "aday partisi IGDB'siz de işlenir");
        assertEquals(2, service.reviewNext(), "Steam toplu çağrısı");

        assertEquals(main, parentOf(child));
        assertNull(parentOf(orphan));
        assertEquals(0, provider.queries.size(), "IGDB'ye hiç gidilmez");
        assertEquals(1, provider.steamQueries.size());
        assertEquals(DlcParentService.Outcome.LINKED_VIA_STEAM, service.totals().keySet().stream().filter(o -> o == DlcParentService.Outcome.LINKED_VIA_STEAM).findFirst().orElseThrow());
    }

    @Test void aDlcWhoseParentIsNotYetClassifiedIsReviewedAgainAfterSixHoursNotSevenDays() {
        provider.igdb = false;
        UUID parent = game("100", "UNKNOWN");
        UUID child = game("11", "DLC");
        provider.fullGame.put("11", "100");
        assertEquals(1, service.reviewNext());
        assertEquals(1, service.reviewNext());
        assertNull(parentOf(child), "ana oyun UNKNOWN: bağlanmaz");
        jdbc.update("update games set game_type='MAIN_GAME' where id=?", parent);
        service.resetForTests();
        clock.advance(Duration.ofHours(5));
        assertEquals(-1, service.reviewNext(), "6 saat dolmadan yeniden bakılmaz");
        service.resetForTests();
        clock.advance(Duration.ofHours(2));
        assertEquals(1, service.reviewNext());
        assertEquals(1, service.reviewNext());
        assertEquals(parent, parentOf(child), "ana oyun sınıflanınca DLC 6 saat içinde bağlanır");
    }

    @Test void aSteamFailureWritesNoMarkerKeepsTheBatchPendingAndPausesBeforeRetrying() {
        provider.igdb = false;
        game("100", "MAIN_GAME");
        UUID child = game("11", "DLC");
        provider.fullGame.put("11", "100");
        provider.steamFailure = "RATE_LIMITED";
        assertEquals(1, service.reviewNext());
        assertEquals(-1, service.reviewNext());
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='DLC_PARENT'", Long.class));
        assertNull(parentOf(child));
        provider.steamFailure = null;
        assertEquals(-1, service.reviewNext(), "duraklatma süresi dolmadan denenmez");
        clock.advance(Duration.ofHours(2));
        assertEquals(1, service.reviewNext());
        assertEquals(1L, jdbc.queryForObject("select count(*) from games where parent_game_id is not null", Long.class));
    }
}
