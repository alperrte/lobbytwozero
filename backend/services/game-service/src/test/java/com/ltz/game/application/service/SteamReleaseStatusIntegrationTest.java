package com.ltz.game.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

/**
 * Steam çıkış durumu taraması (erken erişim, ücretsiz, yakında): değer yalnız Steam oyunu tanıyınca yazılır; yönetici erken erişim değeri ezilmez;
 * ücretsiz/yakında Steam'e aittir ve oyun çıkınca güncellenir; tanınmayan oyun bilinmiyor kalır; sıcak oyun kısa, diğerleri uzun aralıkla yeniden
 * sorulur; sayfası açılan oyun hemen sorulur; hata duraklatır.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false",
        "ltz.game.refresh.release.hot=PT1H", "ltz.game.refresh.release.other=PT24H"})
@Import(SteamReleaseStatusIntegrationTest.Fakes.class)
class SteamReleaseStatusIntegrationTest extends PostgresTestSupport {

    @TestConfiguration static class Fakes {
        @Bean @Primary FlagProvider flagProvider() { return new FlagProvider(); }
        @Bean @Primary FlagClock flagClock() { return new FlagClock(); }
    }

    static class FlagClock extends Clock {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class FlagProvider implements GameProvider {
        final Map<String, ReleaseStatus> flags = new HashMap<>();
        final List<List<String>> queries = new CopyOnWriteArrayList<>();
        String failure;
        boolean background = true;
        boolean essentialSeen;
        @Override public boolean backgroundWorkAllowed() { return background; }
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, ReleaseStatus> releaseStatus(java.util.Collection<String> ids) {
            queries.add(List.copyOf(ids));
            essentialSeen = com.ltz.game.application.port.ProviderPriority.isEssential();
            if (failure != null) throw new ProviderException(failure);
            Map<String, ReleaseStatus> found = new HashMap<>();
            ids.forEach(id -> { if (flags.containsKey(id)) found.put(id, flags.get(id)); });
            return found;
        }
    }

    @Autowired FlagProvider provider;
    @Autowired FlagClock clock;
    @Autowired SteamReleaseStatusService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.flags.clear(); provider.queries.clear(); provider.failure = null; provider.background = true; provider.essentialSeen = false;
        service.resetForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    private UUID game(String appId, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,?,?,'EXTERNAL',?,now(),now())", id, "G" + appId, "g" + appId, type);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        return id;
    }

    private void steam(String appId, boolean early, boolean free, boolean soon) { provider.flags.put(appId, new GameProvider.ReleaseStatus(early, free, soon)); }
    private void steam(String appId, boolean adult, Integer storeType) { provider.flags.put(appId, new GameProvider.ReleaseStatus(false, false, false, adult, storeType)); }
    private Boolean flag(String column, UUID game) { return jdbc.queryForObject("select " + column + " from games where id=?", Boolean.class, game); }
    private Boolean earlyAccess(UUID game) { return flag("early_access", game); }

    private String state(UUID game, String field) {
        var rows = jdbc.queryForList("select data_state from game_metadata_provenance where game_id=? and field=?", String.class, game, field);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Test void statusIsWrittenOnlyWhereSteamRecognisesTheGameAndNeverOverwritesAnAdminEarlyAccessValueOrTouchesNonMainGames() {
        UUID early = game("1", "MAIN_GAME"), released = game("2", "MAIN_GAME"), admin = game("3", "MAIN_GAME"), unknown = game("4", "MAIN_GAME"),
                dlc = game("5", "DLC"), free = game("6", "MAIN_GAME"), soon = game("7", "MAIN_GAME");
        jdbc.update("update games set early_access=false where id=?", admin);
        steam("1", true, false, false);
        steam("2", false, false, false);
        steam("3", true, false, false);
        steam("5", true, true, true);
        steam("6", false, true, false);
        steam("7", false, false, true);

        assertEquals(6, service.nextBatch());

        assertEquals(List.of(List.of("1", "2", "3", "4", "6", "7")), provider.queries.stream().map(q -> q.stream().sorted().toList()).toList(), "ana oyun olmayan kayıt sorulmaz");
        assertEquals(Boolean.TRUE, earlyAccess(early));
        assertEquals(Boolean.FALSE, earlyAccess(released));
        assertEquals(Boolean.FALSE, earlyAccess(admin), "yönetici erken erişim değeri ezilmez");
        assertNull(earlyAccess(unknown), "Steam tanımadı -> bilinmiyor; false varsayılmaz");
        assertNull(earlyAccess(dlc));
        assertEquals(Boolean.FALSE, flag("free", released));
        assertEquals(Boolean.FALSE, flag("coming_soon", released));
        assertEquals(Boolean.TRUE, flag("free", free));
        assertEquals(Boolean.TRUE, flag("coming_soon", soon));
        assertNull(flag("free", unknown), "Steam tanımadı -> ücretsiz bilinmiyor");
        assertNull(flag("coming_soon", unknown));
        assertNull(flag("free", dlc));
        assertEquals("AVAILABLE", state(early, "EARLY_ACCESS"));
        assertEquals("UNKNOWN", state(admin, "EARLY_ACCESS"), "yönetici değeri Steam'in değeri sayılmaz");
        assertEquals("UNKNOWN", state(unknown, "EARLY_ACCESS"));
        assertEquals("AVAILABLE", state(released, "RELEASE_STATUS"));
        assertEquals("UNKNOWN", state(unknown, "RELEASE_STATUS"));
        assertEquals("STEAM", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='RELEASE_STATUS'", String.class, early));
        assertEquals(-1, service.nextBatch(), "işlenen oyun aralığı dolmadan yeniden sorulmaz");
        assertEquals(1, provider.queries.size());
    }

    @Test void hotGamesAreAskedAgainAfterTheShortIntervalAndOthersAfterTheLongOne() {
        UUID soon = game("1", "MAIN_GAME"), plain = game("2", "MAIN_GAME");
        jdbc.update("update games set release_date=?::date where id=?", "2026-11-20", soon);   // çıkışı 30 gün içinde: sıcak
        steam("1", false, false, true);
        steam("2", false, false, false);
        assertEquals(2, service.nextBatch());
        clock.advance(Duration.ofMinutes(59));
        service.resetForTests();
        assertEquals(-1, service.nextBatch(), "sıcak oyun 1 saatten önce yeniden sorulmaz");
        clock.advance(Duration.ofMinutes(2));
        service.resetForTests();
        provider.queries.clear();
        assertEquals(1, service.nextBatch(), "yakında olan oyun sıcaktır: 1 saat sonra sorulur");
        assertEquals(List.of(List.of("1")), provider.queries, "diğer oyun henüz sorulmaz");
        clock.advance(Duration.ofHours(23));
        service.resetForTests();
        provider.queries.clear();
        assertEquals(2, service.nextBatch(), "24 saat dolunca sıcak olmayan oyun da sorulur");
        assertEquals(Boolean.TRUE, flag("coming_soon", soon));
        assertEquals(Boolean.FALSE, flag("coming_soon", plain));
    }

    @Test void aComingSoonGameWithoutANearReleaseDateIsNotHotSoItIsAskedOnTheLongIntervalOnly() {
        game("1", "MAIN_GAME");
        steam("1", false, false, true);
        assertEquals(1, service.nextBatch());
        clock.advance(Duration.ofHours(2));
        service.resetForTests();
        provider.queries.clear();
        assertEquals(-1, service.nextBatch(), "tarihi belirsiz yakında oyunu sıcak değil: 1 saatte yeniden sorulmaz");
        clock.advance(Duration.ofHours(23));
        service.resetForTests();
        assertEquals(1, service.nextBatch(), "uzun aralık dolunca sorulur");
    }

    @Test void freeAndComingSoonBelongToSteamAndAreUpdatedWhenTheGameReleases() {
        UUID id = game("1", "MAIN_GAME");
        jdbc.update("update games set release_date=?::date where id=?", "2026-11-20", id);   // çıkışı 30 gün içinde: sıcak (1 saatte yeniden sorulur)
        steam("1", false, false, true);
        assertEquals(1, service.nextBatch());
        assertEquals(Boolean.TRUE, flag("coming_soon", id));
        clock.advance(Duration.ofMinutes(61));
        service.resetForTests();
        steam("1", false, true, false);
        assertEquals(1, service.nextBatch());
        assertEquals(Boolean.FALSE, flag("coming_soon", id), "oyun çıkınca yakında kalkar");
        assertEquals(Boolean.TRUE, flag("free", id));
    }

    @Test void aValueSteamWroteIsUpdatedWhenTheGameLeavesEarlyAccessAndAdminValuesAreNotOverwrittenOnRecheck() {
        UUID steamOwned = game("1", "MAIN_GAME"), admin = game("2", "MAIN_GAME");
        steam("1", true, false, false);
        steam("2", false, false, false);
        jdbc.update("update games set early_access=true where id=?", admin);
        assertEquals(2, service.nextBatch());
        assertEquals(Boolean.TRUE, earlyAccess(steamOwned));
        assertEquals(Boolean.TRUE, earlyAccess(admin));
        clock.advance(Duration.ofHours(2));
        service.resetForTests();
        steam("1", false, false, false);
        steam("2", false, false, false);
        assertEquals(2, service.nextBatch(), "erken erişimdeki oyunlar sıcaktır: 1 saat sonra yeniden sorulur");
        assertEquals(Boolean.FALSE, earlyAccess(steamOwned), "Steam'in yazdığı değer güncellenir");
        assertEquals("AVAILABLE", state(steamOwned, "EARLY_ACCESS"));
        assertEquals(Boolean.TRUE, earlyAccess(admin), "yönetici değeri yeniden incelemede de ezilmez");
    }

    @Test void aUnrecognisedGameKeepsEarlierSteamValues() {
        UUID id = game("1", "MAIN_GAME");
        steam("1", true, true, true);
        assertEquals(1, service.nextBatch());
        clock.advance(Duration.ofHours(2));
        service.resetForTests();
        provider.flags.clear();                               // Steam artık tanımıyor
        assertEquals(1, service.nextBatch());
        assertEquals(Boolean.TRUE, earlyAccess(id), "tanınmayan oyunda önceki Steam değeri silinmez");
        assertEquals(Boolean.TRUE, flag("free", id));
        assertEquals(Boolean.TRUE, flag("coming_soon", id));
        assertEquals("UNKNOWN", state(id, "RELEASE_STATUS"));
    }

    @Test void whenTheBackgroundShareOfTheQuotaIsUsedUpOnlyHotGamesAreScannedAndAsEssentialWork() {
        UUID hot = game("1", "MAIN_GAME"), plain = game("2", "MAIN_GAME");
        jdbc.update("update games set early_access=true where id=?", hot);
        steam("1", true, false, false);
        steam("2", false, false, false);
        provider.background = false;

        assertEquals(1, service.nextBatch());

        assertEquals(List.of(List.of("1")), provider.queries, "yalnız sıcak oyun sorulur");
        assertEquals(true, provider.essentialSeen, "sıcak tarama öncelikli çağrıdır");
        assertNull(flag("free", plain), "sıcak olmayan oyun kota yenilenene kadar bekler");
        provider.background = true;
        service.resetForTests();
        provider.queries.clear();
        assertEquals(1, service.nextBatch(), "kota payı açılınca diğerleri de taranır");
        assertEquals(List.of(List.of("2")), provider.queries);
        assertEquals(false, provider.essentialSeen, "normal tarama arka plandır");
    }

    @Test void adultContentComesFromSteamAndIsUpdatedOnRecheck() {
        UUID id = game("1", "MAIN_GAME"), plain = game("2", "MAIN_GAME");
        steam("1", true, 0);
        steam("2", false, 0);
        assertEquals(2, service.nextBatch());
        assertEquals(Boolean.TRUE, flag("adult", id));
        assertEquals(Boolean.FALSE, flag("adult", plain));
        clock.advance(Duration.ofHours(25));
        service.resetForTests();
        steam("1", false, 0);
        assertEquals(2, service.nextBatch());
        assertEquals(Boolean.FALSE, flag("adult", id), "Steam içerik tanımlayıcıyı kaldırınca +18 işareti kalkar");
    }

    @Test void aGameIsMarkedDelistedOnlyAfterTwoUnrecognisedObservationsAtLeastTwentyHoursApartAndRecoversWhenSteamKnowsItAgain() {
        UUID id = game("1", "MAIN_GAME");
        assertEquals(1, service.nextBatch());
        assertNull(delisted(id), "ilk tanınmadı gözlemi yalnız not edilir");
        assertEquals("UNKNOWN", state(id, "STEAM_UNRECOGNISED"));
        clock.advance(Duration.ofHours(7));
        due(id);
        service.resetForTests();
        assertEquals(1, service.nextBatch());
        assertNull(delisted(id), "20 saat dolmadı");
        clock.advance(Duration.ofHours(14));
        due(id);
        service.resetForTests();
        assertEquals(1, service.nextBatch());
        assertNotNull(delisted(id), "21 saat sonraki ikinci gözlem işaretler");
        Instant marked = delisted(id);
        clock.advance(Duration.ofHours(30));
        due(id);
        service.resetForTests();
        assertEquals(1, service.nextBatch());
        assertEquals(marked, delisted(id), "özgün kaldırılma zamanı korunur");
        steam("1", false, 0);
        clock.advance(Duration.ofHours(30));
        due(id);
        service.resetForTests();
        assertEquals(1, service.nextBatch());
        assertNull(delisted(id), "Steam yeniden tanıyınca işaret kalkar");
        assertNull(state(id, "STEAM_UNRECOGNISED"), "tanınma zinciri sıfırlar");
    }

    @Test void aProviderFailureBreaksNeitherTheChainNorMarksAnything() {
        UUID id = game("1", "MAIN_GAME");
        assertEquals(1, service.nextBatch());
        clock.advance(Duration.ofHours(21));
        due(id);
        provider.failure = "RATE_LIMITED";
        service.resetForTests();
        assertEquals(-1, service.nextBatch());
        assertNull(delisted(id), "hata gözlem sayılmaz");
        provider.failure = null;
        service.resetForTests();
        clock.advance(Duration.ofHours(2));
        assertEquals(1, service.nextBatch());
        assertNotNull(delisted(id), "hata zinciri bozmaz: özgün ilk gözlemden 20 saat geçti");
    }

    /** İncelemeyi kademenin bekleme süresinden bağımsız olarak yeniden uygun yapar (zinciri taşıyan STEAM_UNRECOGNISED gözlemine dokunmaz). */
    private void due(UUID game) {
        jdbc.update("update game_metadata_provenance set recorded_at = timestamptz '2026-01-01 00:00:00+00' where game_id=? and field='RELEASE_STATUS'", game);
    }

    @Test void aSteamClassifiedGameWhoseStoreTypeIsNoLongerAGameReturnsToUnknownButOtherEvidenceIsKept() {
        UUID steamTyped = game("1", "MAIN_GAME"), igdbTyped = game("2", "MAIN_GAME"), unmarked = game("3", "MAIN_GAME");
        jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'GAME_TYPE','STEAM','AVAILABLE',now())", steamTyped);
        jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'STEAM_TYPE','STEAM','AVAILABLE',now())", steamTyped);
        jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'GAME_TYPE','IGDB','AVAILABLE',now())", igdbTyped);
        steam("1", false, 4);
        steam("2", false, 4);
        steam("3", false, 1);
        assertEquals(3, service.nextBatch());
        assertEquals("UNKNOWN", jdbc.queryForObject("select game_type from games where id=?", String.class, steamTyped));
        assertEquals("UNKNOWN", state(steamTyped, "GAME_TYPE"));
        assertNull(state(steamTyped, "STEAM_TYPE"), "yeniden sınıflansın diye Steam tür gözlemi silinir");
        assertEquals("MAIN_GAME", jdbc.queryForObject("select game_type from games where id=?", String.class, igdbTyped), "IGDB kanıtlı tür korunur");
        assertEquals("MAIN_GAME", jdbc.queryForObject("select game_type from games where id=?", String.class, unmarked), "kanıtı bilinmeyen tür korunur");
    }

    private Instant delisted(UUID game) {
        var value = jdbc.queryForObject("select delisted_at from games where id=?", java.sql.Timestamp.class, game);
        return value == null ? null : value.toInstant();
    }

    @Test void gamesWhosePageIsOpenedAreAskedImmediatelyRegardlessOfTheInterval() {
        UUID id = game("1", "MAIN_GAME"), other = game("2", "MAIN_GAME");
        steam("1", false, false, false);
        steam("2", false, false, false);
        assertEquals(2, service.nextBatch());
        provider.queries.clear();
        steam("1", false, true, false);
        assertEquals(1, service.refreshNow(List.of(id)));
        assertEquals(List.of(List.of("1")), provider.queries, "yalnız istenen oyun sorulur");
        assertEquals(Boolean.TRUE, flag("free", id));
        assertEquals(Boolean.FALSE, flag("free", other));
        assertEquals(-1, service.refreshNow(List.of()));
        assertEquals(-1, service.refreshNow(List.of(UUID.randomUUID())), "Steam referansı olmayan oyun sorulmaz");
    }

    @Test void providerFailureWritesNothingAndPausesBeforeRetrying() {
        UUID id = game("1", "MAIN_GAME");
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, service.nextBatch());
        assertNull(earlyAccess(id));
        assertNull(state(id, "RELEASE_STATUS"));
        assertEquals(-1, service.nextBatch(), "duraklatma süresi dolmadan sağlayıcıya gidilmez");
        assertEquals(-1, service.refreshNow(List.of(id)), "ipucu tahliyesi de duraklamada sağlayıcıya gitmez");
        assertEquals(1, provider.queries.size());
        clock.advance(Duration.ofHours(2));
        provider.failure = null;
        steam("1", true, false, false);
        assertEquals(1, service.nextBatch());
        assertEquals(Boolean.TRUE, earlyAccess(id));
    }
}
