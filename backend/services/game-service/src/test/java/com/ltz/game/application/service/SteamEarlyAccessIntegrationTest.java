package com.ltz.game.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Steam erken erişim taraması: değer yalnız Steam oyunu tanıyınca yazılır; yönetici değeri ezilmez; Steam'in kendi yazdığı değer
 * oyun erken erişimden çıkınca güncellenir; tanınmayan oyun bilinmiyor kalır; gözlem 30 gün sonra yenilenir; hata duraklatır.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
@Import(SteamEarlyAccessIntegrationTest.Fakes.class)
class SteamEarlyAccessIntegrationTest extends PostgresTestSupport {

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
        final Map<String, Boolean> flags = new HashMap<>();
        final List<List<String>> queries = new CopyOnWriteArrayList<>();
        String failure;
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, Boolean> earlyAccess(java.util.Collection<String> ids) {
            queries.add(List.copyOf(ids));
            if (failure != null) throw new ProviderException(failure);
            Map<String, Boolean> found = new HashMap<>();
            ids.forEach(id -> { if (flags.containsKey(id)) found.put(id, flags.get(id)); });
            return found;
        }
    }

    @Autowired FlagProvider provider;
    @Autowired FlagClock clock;
    @Autowired SteamEarlyAccessService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.flags.clear(); provider.queries.clear(); provider.failure = null;
        service.resetForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    private UUID game(String appId, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,?,?,'EXTERNAL',?,now(),now())", id, "G" + appId, "g" + appId, type);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        return id;
    }

    private Boolean earlyAccess(UUID game) { return jdbc.queryForObject("select early_access from games where id=?", Boolean.class, game); }

    private String state(UUID game) {
        var rows = jdbc.queryForList("select data_state from game_metadata_provenance where game_id=? and field='EARLY_ACCESS'", String.class, game);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Test void flagIsWrittenOnlyWhereSteamRecognisesTheGameAndNeverOverwritesAnAdminValueOrTouchesNonMainGames() {
        UUID early = game("1", "MAIN_GAME"), released = game("2", "MAIN_GAME"), admin = game("3", "MAIN_GAME"), unknown = game("4", "MAIN_GAME"), dlc = game("5", "DLC");
        jdbc.update("update games set early_access=false where id=?", admin);
        provider.flags.put("1", true);
        provider.flags.put("2", false);
        provider.flags.put("3", true);
        provider.flags.put("5", true);

        assertEquals(4, service.nextBatch());

        assertEquals(List.of(List.of("1", "2", "3", "4")), provider.queries.stream().map(q -> q.stream().sorted().toList()).toList(), "ana oyun olmayan kayıt sorulmaz");
        assertEquals(Boolean.TRUE, earlyAccess(early));
        assertEquals(Boolean.FALSE, earlyAccess(released));
        assertEquals(Boolean.FALSE, earlyAccess(admin), "yönetici değeri ezilmez");
        assertNull(earlyAccess(unknown), "Steam tanımadı -> bilinmiyor; false varsayılmaz");
        assertNull(earlyAccess(dlc));
        assertEquals("AVAILABLE", state(early));
        assertEquals("AVAILABLE", state(released));
        assertEquals("UNKNOWN", state(admin), "yönetici değeri Steam'in değeri sayılmaz");
        assertEquals("UNKNOWN", state(unknown));
        assertEquals("STEAM", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='EARLY_ACCESS'", String.class, early));
        assertEquals(-1, service.nextBatch(), "işlenen oyun yeniden sorulmaz");
        assertEquals(1, provider.queries.size());
    }

    @Test void aValueSteamWroteIsUpdatedWhenTheGameLeavesEarlyAccessAfterTheRecheckInterval() {
        UUID id = game("1", "MAIN_GAME");
        provider.flags.put("1", true);
        assertEquals(1, service.nextBatch());
        assertEquals(Boolean.TRUE, earlyAccess(id));
        clock.advance(Duration.ofDays(29));
        service.resetForTests();
        assertEquals(-1, service.nextBatch(), "30 günden önce yeniden sorulmaz");
        clock.advance(Duration.ofDays(2));
        service.resetForTests();
        provider.flags.put("1", false);
        assertEquals(1, service.nextBatch());
        assertEquals(Boolean.FALSE, earlyAccess(id), "Steam'in yazdığı değer güncellenir");
        assertEquals("AVAILABLE", state(id));
    }

    @Test void aUnrecognisedGameKeepsAnEarlierSteamValueAndAdminValuesAreStillNotOverwrittenOnRecheck() {
        UUID steamOwned = game("1", "MAIN_GAME"), admin = game("2", "MAIN_GAME");
        provider.flags.put("1", true);
        provider.flags.put("2", false);
        jdbc.update("update games set early_access=true where id=?", admin);
        assertEquals(2, service.nextBatch());
        assertEquals(Boolean.TRUE, earlyAccess(admin));
        clock.advance(Duration.ofDays(31));
        service.resetForTests();
        provider.flags.clear();                               // Steam artık hiçbirini tanımıyor
        provider.flags.put("2", false);
        assertEquals(2, service.nextBatch());
        assertEquals(Boolean.TRUE, earlyAccess(steamOwned), "tanınmayan oyunda önceki Steam değeri silinmez");
        assertEquals("AVAILABLE", state(steamOwned));
        assertEquals(Boolean.TRUE, earlyAccess(admin), "yönetici değeri yeniden incelemede de ezilmez");
    }

    @Test void providerFailureWritesNothingAndPausesBeforeRetrying() {
        UUID id = game("1", "MAIN_GAME");
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, service.nextBatch());
        assertNull(earlyAccess(id));
        assertNull(state(id));
        assertEquals(-1, service.nextBatch(), "duraklatma süresi dolmadan sağlayıcıya gidilmez");
        assertEquals(1, provider.queries.size());
        clock.advance(Duration.ofHours(2));
        provider.failure = null;
        provider.flags.put("1", true);
        assertEquals(1, service.nextBatch());
        assertEquals(Boolean.TRUE, earlyAccess(id));
    }
}
