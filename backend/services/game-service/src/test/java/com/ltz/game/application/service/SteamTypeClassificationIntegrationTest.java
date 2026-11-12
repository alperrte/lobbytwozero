package com.ltz.game.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * Steam tür yedeği: IGDB'yi beklemeden type=0 → ana oyun, type=4 → DLC; başka tür, tanınmayan ve görünmez kayıt UNKNOWN kalır; yönetici kilitli
 * tür ve zaten sınıflı kayıt dokunulmaz; çıkış tarihi yalnız boşsa, terimler yalnız mevcut kodlarla ve yalnız boş türe yazılır; sağlayıcı hatası
 * işaret yazmadan duraklatır; UNKNOWN kayıt 7 gün sonra yeniden sorulur.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
@Import(SteamTypeClassificationIntegrationTest.Fakes.class)
class SteamTypeClassificationIntegrationTest extends PostgresTestSupport {

    @TestConfiguration static class Fakes {
        @Bean @Primary TypeProvider typeProvider() { return new TypeProvider(); }
        @Bean @Primary TypeClock typeClock() { return new TypeClock(); }
    }

    static class TypeClock extends Clock {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class TypeProvider implements GameProvider {
        final Map<String, StoreType> types = new HashMap<>();
        final List<List<String>> queries = new CopyOnWriteArrayList<>();
        String failure;
        String tagFailure;
        int tagCalls;
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, StoreType> storeTypes(java.util.Collection<String> ids) {
            queries.add(List.copyOf(ids));
            if (failure != null) throw new ProviderException(failure);
            Map<String, StoreType> found = new HashMap<>();
            ids.forEach(id -> { if (types.containsKey(id)) found.put(id, types.get(id)); });
            return found;
        }
        @Override public Map<Integer, String> storeTagNames() {
            tagCalls++;
            if (tagFailure != null) throw new ProviderException(tagFailure);
            return Map.of(492, "Indie", 21, "Adventure", 19, "Action", 597, "Casual");
        }
    }

    @Autowired TypeProvider provider;
    @Autowired TypeClock clock;
    @Autowired SteamTypeClassificationService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.types.clear(); provider.queries.clear(); provider.failure = null; provider.tagFailure = null; provider.tagCalls = 0;
        service.resetForTests();
        jdbc.update("delete from game_terms"); jdbc.update("delete from game_field_overrides");
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
        for (String[] term : new String[][]{{"GENRE", "indie"}, {"GENRE", "adventure"}, {"TAG", "action"}, {"CATEGORY", "single-player"}, {"PLATFORM", "win"}}) {
            jdbc.update("insert into catalog_terms(id,kind,code) values (gen_random_uuid(),?,?) on conflict (kind,code) do nothing", term[0], term[1]);
        }
    }

    private UUID game(String appId, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,?,?,'EXTERNAL',?,now(),now())", id, "G" + appId, "g" + appId, type);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        return id;
    }

    private void steam(String appId, int type) { steam(appId, type, true, null, null); }

    private void steam(String appId, int type, boolean visible, LocalDate released, String parent) {
        provider.types.put(appId, new GameProvider.StoreType(type, visible, released, parent, Set.of(492, 21, 19, 597), Set.of(2), Set.of("windows")));
    }

    private String type(UUID game) { return jdbc.queryForObject("select game_type from games where id=?", String.class, game); }
    private String state(UUID game, String field) {
        var rows = jdbc.queryForList("select data_state from game_metadata_provenance where game_id=? and field=?", String.class, game, field);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private List<String> terms(UUID game, String kind) {
        return jdbc.queryForList("select t.code from game_terms gt join catalog_terms t on t.id=gt.term_id where gt.game_id=? and t.kind=? order by t.code", String.class, game, kind);
    }

    @Test void gameAndDlcTypesAreClassifiedFromSteamWithoutWaitingForIgdbAndOthersStayUnknown() {
        UUID main = game("1", "UNKNOWN"), dlc = game("2", "UNKNOWN"), demo = game("3", "UNKNOWN"), hidden = game("4", "UNKNOWN"), missing = game("5", "UNKNOWN");
        steam("1", 0); steam("2", 4); steam("3", 1); steam("4", 0, false, null, null);

        assertEquals(5, service.nextBatch());

        assertEquals("MAIN_GAME", type(main));
        assertEquals("DLC", type(dlc));
        assertEquals("UNKNOWN", type(demo), "demo listeye girmez");
        assertEquals("UNKNOWN", type(hidden), "görünmez kayıt UNKNOWN kalır");
        assertEquals("UNKNOWN", type(missing), "Steam tanımadı");
        assertEquals("AVAILABLE", state(main, "STEAM_TYPE"));
        assertEquals("AVAILABLE", state(main, "GAME_TYPE"));
        assertEquals("STEAM", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='GAME_TYPE'", String.class, main));
        assertEquals("UNKNOWN", state(demo, "STEAM_TYPE"));
        assertNull(state(demo, "GAME_TYPE"), "bilinmeyen tür IGDB'nin kendi gözlemine karışmaz");
        assertEquals("UNKNOWN", state(missing, "STEAM_TYPE"));
    }

    @Test void termsReleaseDateAndOnlyExistingCodesAreWrittenAndNothingIsInvented() {
        UUID id = game("1", "UNKNOWN");
        steam("1", 0, true, LocalDate.parse("2026-11-01"), null);
        assertEquals(1, service.nextBatch());
        assertEquals(List.of("adventure", "indie"), terms(id, "GENRE"));
        assertEquals(List.of("action"), terms(id, "TAG"));
        assertEquals(List.of("single-player"), terms(id, "CATEGORY"));
        assertEquals(List.of("win"), terms(id, "PLATFORM"));
        assertEquals(LocalDate.parse("2026-11-01"), jdbc.queryForObject("select release_date from games where id=?", LocalDate.class, id));
        assertEquals("STEAM", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='RELEASE_DATE'", String.class, id));
        assertEquals("AVAILABLE", state(id, "GENRES"));
        assertEquals(0L, jdbc.queryForObject("select count(*) from catalog_terms where code in ('casual')", Long.class), "eşlemesi olmayan Casual terimi oluşturulmaz");
    }

    @Test void existingReleaseDateAndExistingTermsOfAKindAreNotOverwritten() {
        UUID id = game("1", "UNKNOWN");
        jdbc.update("update games set release_date=date '2020-01-01' where id=?", id);
        jdbc.update("insert into game_terms(game_id,term_id) select ?, id from catalog_terms where kind='GENRE' and code='adventure'", id);
        steam("1", 0, true, LocalDate.parse("2026-11-01"), null);
        assertEquals(1, service.nextBatch());
        assertEquals(LocalDate.parse("2020-01-01"), jdbc.queryForObject("select release_date from games where id=?", LocalDate.class, id), "mevcut tarih ezilmez");
        assertEquals(List.of("adventure"), terms(id, "GENRE"), "bu türde terim varsa Steam eklemez");
        assertEquals(List.of("action"), terms(id, "TAG"), "terimi olmayan tür yine doldurulur");
    }

    @Test void adminLockedAlreadyClassifiedAndDelistedGamesAreNeverTouched() {
        UUID locked = game("1", "UNKNOWN"), classified = game("2", "MAIN_GAME"), delisted = game("3", "UNKNOWN"), igdbDlc = game("4", "DLC");
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at,version) values (?,?,'GAME_TYPE',?,now(),0)", UUID.randomUUID(), locked, UUID.randomUUID());
        jdbc.update("update games set delisted_at=now() where id=?", delisted);
        steam("1", 0); steam("2", 4); steam("3", 0); steam("4", 0);

        assertEquals(-1, service.nextBatch(), "aday yok");

        assertEquals("UNKNOWN", type(locked));
        assertEquals("MAIN_GAME", type(classified));
        assertEquals("UNKNOWN", type(delisted));
        assertEquals("DLC", type(igdbDlc));
        assertEquals(0, provider.queries.size(), "sağlayıcıya gidilmez");
    }

    @Test void newGamesAreAskedBeforeUnknownRecheckAndUnknownIsAskedAgainOnlyAfterSevenDays() {
        UUID unknown = game("1", "UNKNOWN");
        steam("1", 1);
        assertEquals(1, service.nextBatch());
        assertEquals("UNKNOWN", state(unknown, "STEAM_TYPE"));
        service.resetForTests();
        clock.advance(Duration.ofDays(6));
        assertEquals(-1, service.nextBatch(), "7 gün dolmadan yeniden sorulmaz");
        service.resetForTests();
        clock.advance(Duration.ofDays(2));
        steam("1", 0);
        assertEquals(1, service.nextBatch());
        assertEquals("MAIN_GAME", type(unknown), "Steam sonradan türü verirse sınıflanır");
    }

    @Test void providerOrTagListFailureWritesNothingAndPausesBeforeRetrying() {
        UUID id = game("1", "UNKNOWN");
        steam("1", 0);
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, service.nextBatch());
        assertNull(state(id, "STEAM_TYPE"));
        assertEquals(-1, service.nextBatch(), "duraklatma süresi dolmadan sağlayıcıya gidilmez");
        assertEquals(1, provider.queries.size());
        clock.advance(Duration.ofHours(2));
        provider.failure = null;
        provider.tagFailure = "PROVIDER_UNAVAILABLE";
        assertEquals(-1, service.nextBatch());
        assertEquals("UNKNOWN", type(id), "etiket adları alınamazsa terimsiz sınıflama yapılmaz");
        assertNull(state(id, "STEAM_TYPE"));
        clock.advance(Duration.ofHours(2));
        provider.tagFailure = null;
        assertEquals(1, service.nextBatch());
        assertEquals("MAIN_GAME", type(id));
    }

    @Test void tagNamesAreFetchedOncePerDay() {
        game("1", "UNKNOWN"); game("2", "UNKNOWN");
        steam("1", 0); steam("2", 0);
        assertEquals(2, service.nextBatch());
        game("3", "UNKNOWN");
        steam("3", 0);
        assertEquals(1, service.nextBatch());
        assertEquals(1, provider.tagCalls, "etiket adları önbellekten");
        clock.advance(Duration.ofHours(25));
        game("4", "UNKNOWN");
        steam("4", 0);
        assertEquals(1, service.nextBatch());
        assertEquals(2, provider.tagCalls, "24 saat sonra yenilenir");
    }
}
