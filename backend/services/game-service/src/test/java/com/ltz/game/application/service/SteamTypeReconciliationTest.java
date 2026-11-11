package com.ltz.game.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.exception.ProviderException;
import java.sql.Timestamp;
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

/**
 * IGDB uzlaştırması: türü Steam mağaza türünden (yedek) verilmiş kayıt IGDB kanıtı gelince IGDB'ye geçer (tür, kaynak, IGDB'nin sağladığı türdeki terimler);
 * kanıt yoksa Steam yedeği korunur ve yeniden sorma {@code IGDB_TYPE_CHECK} gözlemiyle bekletilir; yönetici kilitli ve IGDB kaynaklı tür aday değildir.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false",
        "ltz.game.refresh.igdb-recheck.recent=P1D", "ltz.game.refresh.igdb-recheck.old=P7D"})
@Import(SteamTypeReconciliationTest.Fakes.class)
class SteamTypeReconciliationTest extends PostgresTestSupport {

    @TestConfiguration static class Fakes {
        @Bean @Primary IgdbProvider reconciliationProvider() { return new IgdbProvider(); }
        @Bean @Primary ReconClock reconciliationClock() { return new ReconClock(); }
    }

    static class ReconClock extends Clock {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class IgdbProvider implements GameProvider {
        final Map<String, String> typeEvidence = new HashMap<>();
        final Map<String, List<TermEvidence>> genreEvidence = new HashMap<>();
        final List<List<String>> typeQueries = new CopyOnWriteArrayList<>();
        @Override public String provider() { return "STEAM"; }
        @Override public String detailSource() { return "IGDB"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG, Capability.DETAILS, Capability.ITEM_TYPE); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, ItemEvidence> itemEvidence(java.util.Collection<String> ids) {
            typeQueries.add(List.copyOf(ids));
            Map<String, ItemEvidence> found = new HashMap<>();
            ids.stream().filter(typeEvidence::containsKey).forEach(id -> found.put(id, new ItemEvidence(typeEvidence.get(id), null, genreEvidence.get(id), null)));
            return found;
        }
    }

    @Autowired IgdbProvider provider;
    @Autowired ReconClock clock;
    @Autowired CatalogClassificationService classification;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.typeEvidence.clear(); provider.genreEvidence.clear(); provider.typeQueries.clear();
        classification.restartPassForTests();
        jdbc.update("delete from game_terms"); jdbc.update("delete from game_field_overrides");
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    private void steamTyped(String appId, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) values (?,?,?,'EXTERNAL',?,'INDEX_ONLY',now(),now())", id, "G" + appId, "g" + appId, type);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'GAME_TYPE','STEAM','AVAILABLE',?)", id, Timestamp.from(clock.now));
    }
    private UUID gameOf(String appId) { return jdbc.queryForObject("select game_id from game_external_refs where external_id=?", UUID.class, appId); }
    private GameType typeOf(String appId) { return GameType.valueOf(jdbc.queryForObject("select game_type from games where id=?", String.class, gameOf(appId))); }
    private void steamTerm(UUID game, String kind, String code, String field) {
        jdbc.update("insert into catalog_terms(id,kind,code) values (gen_random_uuid(),?,?) on conflict (kind,code) do nothing", kind, code);
        jdbc.update("insert into game_terms(game_id,term_id) select ?, id from catalog_terms where kind=? and code=?", game, kind, code);
        jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,?,'STEAM','AVAILABLE',now()) on conflict do nothing", game, field);
    }
    private List<String> termCodes(UUID game, String kind) {
        return jdbc.queryForList("select t.code from game_terms gt join catalog_terms t on t.id=gt.term_id where gt.game_id=? and t.kind=? order by t.code", String.class, game, kind);
    }
    private long markers(UUID game, String field) {
        return jdbc.queryForObject("select count(*) from game_metadata_provenance where game_id=? and field=?", Long.class, game, field);
    }

    @Test void igdbEvidenceTakesOverTheTypeAndItsTermsAndNoEvidenceKeepsTheSteamFallback() {
        steamTyped("1", "MAIN_GAME"); steamTyped("2", "MAIN_GAME"); steamTyped("3", "DLC");
        UUID one = gameOf("1"), two = gameOf("2");
        steamTerm(one, "GENRE", "indie", "GENRES"); steamTerm(one, "TAG", "action", "TAGS"); steamTerm(one, "PLATFORM", "win", "PLATFORMS");
        steamTerm(two, "GENRE", "indie", "GENRES");
        provider.typeEvidence.put("1", "dlc");
        provider.genreEvidence.put("1", List.of(new GameProvider.TermEvidence("adventure", "Adventure")));
        provider.typeEvidence.put("3", "main_game");

        assertEquals(2, classification.classifyNextBatch(), "IGDB kanıtı olan iki Steam sınıflı kayıt güncellenir");

        assertEquals(GameType.DLC, typeOf("1"), "IGDB, Steam yedeğinin yerine geçer");
        assertEquals(GameType.MAIN_GAME, typeOf("3"));
        assertEquals("IGDB", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='GAME_TYPE'", String.class, one), "tür kaynağı detay kaynağına döner");
        assertEquals(List.of("adventure"), termCodes(one, "GENRE"), "IGDB'nin sağladığı türde Steam terimleri IGDB'ninkilerle değişir");
        assertEquals(List.of("action"), termCodes(one, "TAG"), "IGDB'nin vermediği türdeki Steam terimleri korunur");
        assertEquals(List.of("win"), termCodes(one, "PLATFORM"));
        assertEquals(GameType.MAIN_GAME, typeOf("2"), "IGDB kanıt vermedi: Steam yedeği korunur");
        assertEquals(List.of("indie"), termCodes(two, "GENRE"));
        assertEquals("UNKNOWN", jdbc.queryForObject("select data_state from game_metadata_provenance where game_id=? and field='IGDB_TYPE_CHECK'", String.class, two));
        assertEquals(0L, markers(one, "IGDB_TYPE_CHECK"));
    }

    @Test void aSteamClassifiedGameIsAskedAgainOnlyAfterTheRecheckWindow() {
        steamTyped("1", "MAIN_GAME");
        assertEquals(0, classification.classifyNextBatch());
        assertEquals(-1, classification.classifyNextBatch());
        clock.advance(CatalogClassificationService.PASS_INTERVAL.plusMinutes(1));
        int queries = provider.typeQueries.size();
        assertEquals(-1, classification.classifyNextBatch(), "IGDB_TYPE_CHECK bekleme süresi dolmadan yeniden sorulmaz");
        assertEquals(queries, provider.typeQueries.size());
        provider.typeEvidence.put("1", "dlc");
        clock.advance(CatalogClassificationService.RECHECK_AFTER.plusHours(1));
        assertEquals(1, classification.classifyNextBatch());
        assertEquals(GameType.DLC, typeOf("1"));
        clock.advance(CatalogClassificationService.RECHECK_AFTER.plusHours(1));
        classification.restartPassForTests();
        queries = provider.typeQueries.size();
        assertEquals(-1, classification.classifyNextBatch(), "IGDB kaynaklı tür artık aday değildir");
        assertEquals(queries, provider.typeQueries.size());
    }

    @Test void adminLockedAndIgdbClassifiedGamesAreNeverCandidatesForReconciliation() {
        steamTyped("1", "MAIN_GAME");
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at) values (?,?,'GAME_TYPE',?,now())", UUID.randomUUID(), gameOf("1"), UUID.randomUUID());
        steamTyped("2", "MAIN_GAME");
        jdbc.update("update game_metadata_provenance set source='IGDB' where game_id=? and field='GAME_TYPE'", gameOf("2"));
        provider.typeEvidence.put("1", "dlc"); provider.typeEvidence.put("2", "dlc");
        assertEquals(-1, classification.classifyNextBatch());
        assertTrue(provider.typeQueries.isEmpty());
        assertEquals(GameType.MAIN_GAME, typeOf("1"));
        assertEquals(GameType.MAIN_GAME, typeOf("2"));
    }
}
