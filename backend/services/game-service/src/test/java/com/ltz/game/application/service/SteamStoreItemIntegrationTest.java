package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
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
 * Steam toplu mağaza öğeleri: 16:9 başlık görseli IGDB'nin dikey kapağının yerini alır; geliştirici, yayıncı ve dil yalnız boşken dolar;
 * yönetici kapağı/dolu veri ezilmez; veri vermeyen oyun işaretlenir ve yeniden sorulmaz; hata duraklatır.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
@Import(SteamStoreItemIntegrationTest.Fakes.class)
class SteamStoreItemIntegrationTest extends PostgresTestSupport {
    static final String IGDB = "https://images.igdb.com/igdb/image/upload/t_cover_big/co4sbw.jpg";
    static final String ADMIN = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/9/header.jpg";
    static final String HASH = "ce4d5e53b36cb9d3c4309d1df72bf8663bbbc7ef";

    @TestConfiguration static class Fakes {
        @Bean @Primary ItemProvider itemProvider() { return new ItemProvider(); }
        @Bean @Primary MutableClock itemClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class ItemProvider implements GameProvider {
        final Map<String, StoreItem> items = new HashMap<>();
        final List<List<String>> queries = new CopyOnWriteArrayList<>();
        String failure;
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, StoreItem> storeItems(java.util.Collection<String> ids) {
            queries.add(List.copyOf(ids));
            if (failure != null) throw new ProviderException(failure);
            Map<String, StoreItem> found = new HashMap<>();
            ids.forEach(id -> { if (items.containsKey(id)) found.put(id, items.get(id)); });
            return found;
        }
    }

    @Autowired ItemProvider provider;
    @Autowired MutableClock clock;
    @Autowired SteamStoreItemService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.items.clear(); provider.queries.clear(); provider.failure = null;
        service.resetForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    private UUID game(String appId, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,?,?,'EXTERNAL',?,now(),now())", id, "G" + appId, "g" + appId, type);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        return id;
    }

    private void cover(UUID game, String url, String owner) {
        jdbc.update("insert into game_media(id,game_id,type,url,provider,sort_order,version) values (?,?,'COVER',?,?,0,0)", UUID.randomUUID(), game, url, owner);
    }

    private String coverOf(UUID game) {
        var rows = jdbc.queryForList("select url from game_media where game_id=? and type='COVER'", String.class, game);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<String> names(String table, UUID game) { return jdbc.queryForList("select name from " + table + " where game_id=? order by name", String.class, game); }

    private static String steam(String app) { return "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/" + app + "/" + HASH + "/header.jpg"; }

    private static GameProvider.LanguageSupport language(String code, boolean audio) {
        return new GameProvider.LanguageSupport(code, LanguageSupportStatus.SUPPORTED, audio ? LanguageSupportStatus.SUPPORTED : LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.UNKNOWN);
    }

    @Test void steamItemsFillOnlyEmptyFieldsReplaceTheIgdbPosterAndNeverTouchAdminCoversOrNonMainGames() {
        UUID empty = game("1", "MAIN_GAME"), poster = game("2", "MAIN_GAME"), admin = game("3", "MAIN_GAME"), noData = game("4", "MAIN_GAME"), dlc = game("5", "DLC");
        cover(poster, IGDB, "IGDB");
        jdbc.update("insert into game_developers(game_id,name) values (?,'Existing Dev')", poster);
        cover(admin, ADMIN, "ADMIN");
        cover(noData, IGDB, "IGDB");
        provider.items.put("1", new GameProvider.StoreItem(steam("1"), Set.of("Infinity Ward"), Set.of("Activision"), List.of(language("en-us", true), language("tr-tr", false))));
        provider.items.put("2", new GameProvider.StoreItem(steam("2"), Set.of("Other Dev"), Set.of("Pub"), List.of(language("en-us", false))));
        provider.items.put("3", new GameProvider.StoreItem(steam("3"), Set.of(), Set.of(), List.of()));
        provider.items.put("5", new GameProvider.StoreItem(steam("5"), Set.of("Dlc Dev"), Set.of(), List.of()));

        assertEquals(4, service.nextBatch());

        assertEquals(List.of(List.of("1", "2", "3", "4")), provider.queries.stream().map(q -> q.stream().sorted().toList()).toList(), "ana oyun olmayan kayıt sorulmaz");
        assertEquals(steam("1"), coverOf(empty));
        assertEquals(List.of("Infinity Ward"), names("game_developers", empty));
        assertEquals(List.of("Activision"), names("game_publishers", empty));
        assertEquals(Set.of("en-us", "tr-tr"), Set.copyOf(jdbc.queryForList("select language_code from game_languages where game_id=?", String.class, empty)));
        assertEquals("AVAILABLE", jdbc.queryForObject("select language_data_state from games where id=?", String.class, empty));
        assertEquals("SUPPORTED", jdbc.queryForObject("select audio_support from game_language_supports where game_id=? and language_code='en-us'", String.class, empty));
        assertEquals("UNKNOWN", jdbc.queryForObject("select audio_support from game_language_supports where game_id=? and language_code='tr-tr'", String.class, empty));
        assertEquals(steam("2"), coverOf(poster), "dikey IGDB kapağı Steam 16:9 görseliyle değişir");
        assertEquals(1L, jdbc.queryForObject("select count(*) from game_media where game_id=? and type='COVER'", Long.class, poster), "kapak çoğalmaz");
        assertEquals(List.of("Existing Dev"), names("game_developers", poster), "dolu geliştirici ezilmez");
        assertEquals(List.of("Pub"), names("game_publishers", poster), "boş yayıncı dolar");
        assertEquals(ADMIN, coverOf(admin), "yönetici kapağı ezilmez");
        assertEquals(IGDB, coverOf(noData), "Steam veri vermeyen oyun mevcut kapağını korur");
        assertNull(coverOf(dlc));
        assertEquals("UNKNOWN", jdbc.queryForObject("select data_state from game_metadata_provenance where game_id=? and field='STEAM_ITEMS'", String.class, noData));
        assertEquals("STEAM", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='DEVELOPERS'", String.class, empty));
        assertEquals(-1, service.nextBatch(), "işlenen oyun yeniden sorulmaz");
        assertEquals(1, provider.queries.size());
    }

    @Test void providerFailureWritesNothingAndPausesBeforeRetrying() {
        UUID game = game("1", "MAIN_GAME");
        provider.items.put("1", new GameProvider.StoreItem(steam("1"), Set.of("Dev"), Set.of(), List.of()));
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, service.nextBatch());
        assertNull(coverOf(game));
        provider.failure = null;
        assertEquals(-1, service.nextBatch(), "duraklatma süresi dolmadan denenmez");
        clock.advance(Duration.ofHours(2));
        assertEquals(1, service.nextBatch());
        assertEquals(steam("1"), coverOf(game));
        assertEquals(List.of("Dev"), names("game_developers", game));
    }
}
