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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PACKAGE → DLC incelemesi: gerçek PostgreSQL/Flyway üzerinde, sahte Steam DLC listesi ve IGDB ilişki kanıtıyla.
 * Yalnız Steam DLC listesi + IGDB 13 + üst oyun kanıtı birlikte varsa DLC olur; bundle, çelişki ve yetersiz kanıt PACKAGE kalır.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
@Import(PackageReviewIntegrationTest.Fakes.class)
class PackageReviewIntegrationTest extends PostgresTestSupport {
    @TestConfiguration static class Fakes {
        @Bean @Primary ReviewProvider reviewProvider() { return new ReviewProvider(); }
        @Bean @Primary MutableClock reviewClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class ReviewProvider implements GameProvider {
        final Set<String> dlc = new java.util.HashSet<>();
        final Map<String, RelationEvidence> relation = new HashMap<>();
        final List<List<String>> relationQueries = new CopyOnWriteArrayList<>();
        int dlcListCalls;
        String failure;
        @Override public String provider() { return "STEAM"; }
        @Override public String detailSource() { return "IGDB"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG, Capability.ITEM_TYPE); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Set<String> catalogDlcIds() {
            dlcListCalls++;
            if (failure != null) throw new ProviderException(failure);
            return Set.copyOf(dlc);
        }
        @Override public Map<String, RelationEvidence> relationEvidence(java.util.Collection<String> ids) {
            relationQueries.add(List.copyOf(ids));
            Map<String, RelationEvidence> found = new HashMap<>();
            ids.forEach(id -> { if (relation.containsKey(id)) found.put(id, relation.get(id)); });
            return found;
        }
    }

    @Autowired ReviewProvider provider;
    @Autowired MutableClock clock;
    @Autowired PackageReviewService review;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.dlc.clear(); provider.relation.clear(); provider.relationQueries.clear(); provider.dlcListCalls = 0; provider.failure = null;
        review.resetForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    private UUID packageGame(String appId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) "
                + "values (?,?,?,'EXTERNAL','PACKAGE','INDEX_ONLY',now(),now())", id, "G" + appId, "g" + appId);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())",
                UUID.randomUUID(), id, appId);
        return id;
    }

    private String typeOf(UUID id) { return jdbc.queryForObject("select game_type from games where id=?", String.class, id); }

    private static GameProvider.RelationEvidence evidence(Integer type, GameProvider.ParentState parent) {
        return new GameProvider.RelationEvidence(type, parent, parent == GameProvider.ParentState.LINKED ? "999" : null);
    }

    @Test void onlySteamDlcWithIgdbPackAndAParentBecomesDlcAndEverythingElseKeepsItsTypeWithoutDuplicates() {
        UUID converted = packageGame("1"), bundle = packageGame("2"), notSteamDlc = packageGame("3"), noParent = packageGame("4");
        UUID otherType = packageGame("5"), noEvidence = packageGame("6"), protectedByAdmin = packageGame("7"), unmappedParent = packageGame("8");
        provider.dlc.addAll(Set.of("1", "2", "4", "5", "6", "7", "8"));   // 3 Steam'de DLC değil
        provider.relation.put("1", evidence(13, GameProvider.ParentState.LINKED));
        provider.relation.put("2", evidence(3, GameProvider.ParentState.LINKED));      // bundle: Steam DLC dese de asla DLC değil
        provider.relation.put("3", evidence(13, GameProvider.ParentState.LINKED));
        provider.relation.put("4", evidence(13, GameProvider.ParentState.NONE));
        provider.relation.put("5", evidence(5, GameProvider.ParentState.LINKED));
        provider.relation.put("7", evidence(13, GameProvider.ParentState.LINKED));
        provider.relation.put("8", evidence(13, GameProvider.ParentState.UNMAPPED));
        jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'GAME_TYPE','ADMIN','AVAILABLE',now())", protectedByAdmin);

        assertEquals(8, review.reviewNextBatch());

        assertEquals("DLC", typeOf(converted));
        assertEquals("DLC", typeOf(unmappedParent), "IGDB üst oyun bildiriyorsa Steam karşılığı eşlenmemiş olsa da kanıt yeterlidir");
        for (UUID kept : List.of(bundle, notSteamDlc, noParent, otherType, noEvidence, protectedByAdmin)) assertEquals("PACKAGE", typeOf(kept));
        assertEquals(8L, jdbc.queryForObject("select count(*) from games", Long.class), "kayıt eklenmez/silinmez");
        assertEquals(8L, jdbc.queryForObject("select count(*) from game_external_refs", Long.class), "dış referanslar korunur");
        assertEquals(8L, jdbc.queryForObject("select count(distinct game_id) from game_external_refs", Long.class), "yinelenen kayıt yok");
        assertEquals(Map.of(PackageReviewService.Outcome.CONVERTED, 2L, PackageReviewService.Outcome.KEPT_BUNDLE, 1L,
                PackageReviewService.Outcome.KEPT_NOT_STEAM_DLC, 1L, PackageReviewService.Outcome.KEPT_NO_PARENT, 1L,
                PackageReviewService.Outcome.KEPT_OTHER_TYPE, 1L, PackageReviewService.Outcome.KEPT_NO_EVIDENCE, 1L,
                PackageReviewService.Outcome.SKIPPED_PROTECTED, 1L), review.totals());
        assertEquals("IGDB", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='GAME_TYPE'", String.class, converted));
        assertEquals("ADMIN", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='GAME_TYPE'", String.class, protectedByAdmin),
                "yönetici kaynaklı tür gözlemi korunur");
        assertEquals(8L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='PACKAGE_REVIEW'", Long.class));
        assertEquals(List.of(List.of("1", "2", "4", "5", "6", "7", "8")), provider.relationQueries, "IGDB yalnız Steam'in DLC dediği kayıtlar için sorulur");
    }

    @Test void reviewedRecordsAreNotAskedAgainUntilTheRecheckWindowPasses() {
        packageGame("1"); packageGame("2");
        provider.dlc.add("1");
        provider.relation.put("1", evidence(3, GameProvider.ParentState.NONE));
        assertEquals(2, review.reviewNextBatch());
        assertEquals(1, provider.dlcListCalls);
        assertEquals(-1, review.reviewNextBatch(), "incelenmiş kayıt aday değil");
        assertEquals(1, provider.relationQueries.size());
        // Boş tur sonrası bekleme ve yeniden inceleme penceresi
        clock.advance(PackageReviewService.RECHECK.plusHours(1));
        provider.dlc.add("2");
        provider.relation.put("2", evidence(13, GameProvider.ParentState.LINKED));
        assertEquals(2, review.reviewNextBatch());
        assertEquals(2, provider.dlcListCalls, "Steam DLC listesi süresi dolunca yenilenir");
        assertEquals("DLC", jdbc.queryForObject("select g.game_type from games g join game_external_refs r on r.game_id=g.id where r.external_id='2'", String.class));
    }

    @Test void providerFailureWritesNoMarkerPausesAndLeavesTheRecordsAsCandidates() {
        UUID id = packageGame("1");
        provider.dlc.add("1");
        provider.relation.put("1", evidence(13, GameProvider.ParentState.LINKED));
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, review.reviewNextBatch());
        assertEquals("PACKAGE", typeOf(id));
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_metadata_provenance where field='PACKAGE_REVIEW'", Long.class));
        provider.failure = null;
        assertEquals(-1, review.reviewNextBatch(), "duraklatma süresi dolmadan yeniden denenmez");
        clock.advance(Duration.ofHours(2));
        assertEquals(1, review.reviewNextBatch());
        assertEquals("DLC", typeOf(id));
    }

    @Test void nonPackageAndInactiveRecordsAreNeverCandidates() {
        UUID main = packageGame("1");
        jdbc.update("update games set game_type='MAIN_GAME' where id=?", main);
        UUID archived = packageGame("2");
        jdbc.update("update games set status='ARCHIVED' where id=?", archived);
        provider.dlc.addAll(Set.of("1", "2"));
        assertEquals(-1, review.reviewNextBatch());
        assertTrue(provider.relationQueries.isEmpty());
        assertEquals("MAIN_GAME", typeOf(main));
    }
}
