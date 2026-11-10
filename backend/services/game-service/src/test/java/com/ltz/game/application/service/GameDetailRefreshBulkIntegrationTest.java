package com.ltz.game.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.LanguageSupportStatus;
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
 * Detayı alınmış oyunların toplu yenilemesi: sıcak oyun kısa, diğerleri uzun aralıkla yeniden sorulur; kanıt yoksa eski detay korunur;
 * ilk alım ve hatalı kayıtlar bu yola girmez; hata duraklatır; sayfası açılan oyun hemen yenilenir.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false",
        "ltz.game.refresh.details.hot=PT2H", "ltz.game.refresh.details.other=PT24H"})
@Import(GameDetailRefreshBulkIntegrationTest.Fakes.class)
class GameDetailRefreshBulkIntegrationTest extends PostgresTestSupport {

    @TestConfiguration static class Fakes {
        @Bean @Primary RefreshProvider refreshProvider() { return new RefreshProvider(); }
        @Bean @Primary RefreshClock refreshClock() { return new RefreshClock(); }
    }

    static class RefreshClock extends Clock {
        Instant now = Instant.parse("2026-11-08T12:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class RefreshProvider implements GameProvider {
        final Map<String, Details> found = new HashMap<>();
        final List<List<String>> queries = new CopyOnWriteArrayList<>();
        String failure;
        @Override public String provider() { return "STEAM"; }
        @Override public String detailSource() { return "IGDB"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG, Capability.DETAILS); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, Details> bulkDetails(java.util.Collection<String> ids) {
            queries.add(List.copyOf(ids));
            if (failure != null) throw new ProviderException(failure);
            Map<String, Details> result = new HashMap<>();
            ids.forEach(id -> { if (found.containsKey(id)) result.put(id, found.get(id)); });
            return result;
        }
    }

    @Autowired RefreshProvider provider;
    @Autowired RefreshClock clock;
    @Autowired GameDetailBulkService bulk;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T12:00:00Z");
        provider.found.clear(); provider.queries.clear(); provider.failure = null;
        bulk.resetForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    /** Detayı {@code ageHours} saat önce alınmış ana oyun. */
    private UUID fetched(String appId, long ageHours) {
        UUID id = UUID.randomUUID();
        Instant at = clock.now.minus(Duration.ofHours(ageHours));
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,description,metadata_completeness,details_fetched_at,details_expires_at,created_at,updated_at) "
                + "values (?,?,?,'EXTERNAL','MAIN_GAME','Old','PARTIAL',?,?,now(),now())", id, "G" + appId, "g" + appId, Timestamp.from(at), Timestamp.from(at.plus(Duration.ofHours(24))));
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        return id;
    }

    private static GameProvider.Details details(String description) {
        return new GameProvider.Details(description, java.time.LocalDate.of(2022, 10, 28), Set.of("Dev"), Set.of("Publisher"), "main_game",
                List.of(new GameProvider.LanguageSupport("en-us", LanguageSupportStatus.SUPPORTED, LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.UNKNOWN)), null);
    }

    private String description(UUID id) { return jdbc.queryForObject("select description from games where id=?", String.class, id); }
    private Instant fetchedAt(UUID id) { return jdbc.queryForObject("select details_fetched_at from games where id=?", Timestamp.class, id).toInstant(); }

    @Test void hotGamesAreRefreshedAfterTheShortIntervalAndOthersAfterTheLongOneAndFreshOnesAreLeftAlone() {
        UUID freshHot = fetched("1", 1), staleHot = fetched("2", 3), staleCold = fetched("3", 25), freshCold = fetched("4", 23);
        jdbc.update("update games set early_access=true where id in (?,?)", freshHot, staleHot);
        for (String app : List.of("1", "2", "3", "4")) provider.found.put(app, details("New " + app));

        assertEquals(2, bulk.refreshNextBatch());

        assertEquals(List.of(List.of("2", "3")), provider.queries.stream().map(q -> q.stream().sorted().toList()).toList(),
                "sıcak 2 saatten, diğerleri 24 saatten eski olanlar sorulur");
        assertEquals("New 2", description(staleHot));
        assertEquals("New 3", description(staleCold));
        assertEquals("Old", description(freshHot));
        assertEquals("Old", description(freshCold));
        assertEquals(clock.now, fetchedAt(staleCold), "deneme zamanı işlenir");
        assertEquals(-1, bulk.refreshNextBatch(), "yenilenen oyun aralığı dolmadan yeniden sorulmaz");
        assertEquals(1, provider.queries.size());
    }

    @Test void aGameTheProviderHasNoEvidenceForKeepsItsOldDetailsAndIsNotAskedAgainEveryRound() {
        UUID id = fetched("1", 30);
        assertEquals(1, bulk.refreshNextBatch());
        assertEquals("Old", description(id), "kanıt yoksa eski detay silinmez");
        assertEquals(clock.now, fetchedAt(id));
        assertEquals(0, jdbc.queryForObject("select details_failure_count from games where id=?", Integer.class, id), "kanıtsızlık hata sayılmaz");
        assertEquals(-1, bulk.refreshNextBatch());
    }

    @Test void aSteamClassifiedGameUnknownToTheDetailSourceIsNotAnErrorAndIsRefreshedLaterButAnIgdbClassifiedOneStillIs() {
        UUID steamTyped = UUID.randomUUID(), igdbTyped = UUID.randomUUID();
        for (var entry : Map.of("1", steamTyped, "2", igdbTyped).entrySet()) {
            jdbc.update("insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) values (?,?,?,'EXTERNAL','MAIN_GAME','INDEX_ONLY',now(),now())",
                    entry.getValue(), "G" + entry.getKey(), "g" + entry.getKey());
            jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), entry.getValue(), entry.getKey());
            jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'GAME_TYPE',?,'AVAILABLE',now())",
                    entry.getValue(), entry.getKey().equals("1") ? "STEAM" : "IGDB");
        }

        assertEquals(2, bulk.bootstrapNextBatch());

        assertEquals(0, jdbc.queryForObject("select details_failure_count from games where id=?", Integer.class, steamTyped), "Steam kaynaklı oyun için kanıt yokluğu hata sayılmaz");
        assertNull(jdbc.queryForObject("select details_last_error_code from games where id=?", String.class, steamTyped));
        assertNotNull(jdbc.queryForObject("select details_fetched_at from games where id=?", Timestamp.class, steamTyped), "kontrol edildi olarak işaretlenir; yenileme turu sonra yeniden sorar");
        assertEquals("PROVIDER_ITEM_NOT_FOUND", jdbc.queryForObject("select details_last_error_code from games where id=?", String.class, igdbTyped), "IGDB kaynaklı oyun eskisi gibi bulunamadı");
        provider.found.put("1", details("Late evidence"));
        clock.advance(Duration.ofHours(25));
        assertEquals(1, bulk.refreshNextBatch());
        assertEquals("Late evidence", description(steamTyped), "detay kaynağı sonradan tanırsa uygulanır");
    }

    @Test void gamesWithoutDetailsOrWithFailuresAreNotRefreshedByThisPath() {
        UUID never = UUID.randomUUID(), failed = fetched("2", 30);
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) values (?,'G1','g1','EXTERNAL','MAIN_GAME','INDEX_ONLY',now(),now())", never);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM','1',now())", UUID.randomUUID(), never);
        jdbc.update("update games set details_failure_count=1, details_last_error_code='PROVIDER_ITEM_NOT_FOUND' where id=?", failed);
        provider.found.put("1", details("New 1"));
        provider.found.put("2", details("New 2"));
        assertEquals(-1, bulk.refreshNextBatch());
        assertEquals(0, provider.queries.size());
    }

    @Test void providerFailureWritesNothingAndPausesTheRefreshWithoutBlockingTheBootstrap() {
        UUID id = fetched("1", 30);
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, bulk.refreshNextBatch());
        assertEquals("Old", description(id));
        assertEquals(-1, bulk.refreshNextBatch(), "duraklatma süresi dolmadan sağlayıcıya gidilmez");
        assertEquals(-1, bulk.refreshNow(List.of(id)));
        assertEquals(1, provider.queries.size());
        clock.advance(Duration.ofHours(2));
        provider.failure = null;
        provider.found.put("1", details("New 1"));
        assertEquals(1, bulk.refreshNextBatch());
        assertEquals("New 1", description(id));
        assertNotNull(fetchedAt(id));
    }

    @Test void gamesWhosePageIsOpenedAreRefreshedImmediatelyRegardlessOfTheInterval() {
        UUID open = fetched("1", 0), other = fetched("2", 0);
        provider.found.put("1", details("New 1"));
        provider.found.put("2", details("New 2"));
        assertEquals(1, bulk.refreshNow(List.of(open)));
        assertEquals(List.of(List.of("1")), provider.queries, "yalnız istenen oyun sorulur");
        assertEquals("New 1", description(open));
        assertEquals("Old", description(other));
        assertEquals(-1, bulk.refreshNow(List.of()));
        assertEquals(-1, bulk.refreshNow(List.of(UUID.randomUUID())), "Steam referansı olmayan oyun sorulmaz");
    }
}
