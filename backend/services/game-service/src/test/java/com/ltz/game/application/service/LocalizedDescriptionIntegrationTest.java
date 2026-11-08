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
 * Yerelleştirilmiş açıklama doldurma (gerçek PostgreSQL): yalnız sağlayıcının gerçekten Türkçe verdiği metin saklanır; İngilizceye düşen ve
 * Türkçe kanıtı taşımayan metin saklanmaz; varsayılan açıklama ve yönetici verisi değişmez; (oyun, dil) tek satır; yapılandırılmamış dil için istek yok.
 */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false", "ltz.game.localized-descriptions.languages=de,en,tr"})
@Import(LocalizedDescriptionIntegrationTest.Fakes.class)
class LocalizedDescriptionIntegrationTest extends PostgresTestSupport {
    static final String TURKISH = "Karanlık bir dünyada hayatta kalmaya çalış ve şehrini kur.";

    @TestConfiguration static class Fakes {
        @Bean @Primary TextProvider textProvider() { return new TextProvider(); }
        @Bean @Primary MutableClock textClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class TextProvider implements GameProvider {
        final Map<String, Map<String, String>> texts = new HashMap<>();   // dil -> appid -> metin
        final List<String> languageCalls = new CopyOnWriteArrayList<>();
        String failure;
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, String> shortDescriptions(java.util.Collection<String> ids, String language) {
            languageCalls.add(language);
            if (failure != null) throw new ProviderException(failure);
            Map<String, String> found = new HashMap<>();
            Map<String, String> all = texts.getOrDefault(language, Map.of());
            ids.forEach(id -> { if (all.containsKey(id)) found.put(id, all.get(id)); });
            return found;
        }
    }

    @Autowired TextProvider provider;
    @Autowired MutableClock clock;
    @Autowired LocalizedDescriptionService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.texts.clear(); provider.languageCalls.clear(); provider.failure = null;
        service.resetForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    private UUID game(String appId, String type, String description) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,description,created_at,updated_at) values (?,?,?,'EXTERNAL',?,?,now(),now())", id, "G" + appId, "g" + appId, type, description);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        return id;
    }

    private List<String> rows(UUID game) { return jdbc.queryForList("select language||':'||description||':'||source from game_localized_descriptions where game_id=?", String.class, game); }

    @Test void onlyGenuineTurkishTextIsStoredAndEnglishFallbackAndForeignTextAreNot() {
        UUID real = game("1", "MAIN_GAME", "IGDB English summary"), fallback = game("2", "MAIN_GAME", null), sameLanguageDifferentText = game("3", "MAIN_GAME", null);
        UUID noEnglish = game("4", "MAIN_GAME", null), noText = game("5", "MAIN_GAME", null), dlc = game("6", "DLC", null);
        provider.texts.put("en", Map.of("1", "An English text.", "2", "Same text.", "3", "English one.", "5", "Only English.", "6", "Dlc text."));
        provider.texts.put("tr", Map.of("1", TURKISH, "2", "Same text.", "3", "A different but still English text.", "4", TURKISH, "6", TURKISH));

        assertEquals(5, service.nextBatch());

        assertEquals(List.of("tr:" + TURKISH + ":STEAM"), rows(real));
        assertEquals(List.of(), rows(fallback), "İngilizceye düşen metin Türkçe sayılmaz");
        assertEquals(List.of(), rows(sameLanguageDifferentText), "İngilizceden farklı ama Türkçe kanıtı olmayan metin kaydedilmez");
        assertEquals(List.of(), rows(noEnglish), "karşılaştırılacak İngilizce metin yoksa kaydedilmez");
        assertEquals(List.of(), rows(noText));
        assertEquals(List.of(), rows(dlc), "ana oyun olmayan kayıt işlenmez");
        assertEquals("IGDB English summary", jdbc.queryForObject("select description from games where id=?", String.class, real), "varsayılan açıklama değişmez");
        assertEquals("AVAILABLE", jdbc.queryForObject("select data_state from game_metadata_provenance where game_id=? and field='STEAM_DESC_TR'", String.class, real));
        assertEquals("EMPTY", jdbc.queryForObject("select data_state from game_metadata_provenance where game_id=? and field='STEAM_DESC_TR'", String.class, fallback));
        assertEquals(List.of("en", "tr"), provider.languageCalls, "yalnız İngilizce (karşılaştırma) ve yapılandırılmış tr istenir; de için istek atılmaz");
        assertEquals(-1, service.nextBatch(), "işaretli oyunlar yeniden sorulmaz");
        assertEquals(2, provider.languageCalls.size());
    }

    @Test void theSameGameAndLanguageNeverGetsADuplicateRowAndARefreshUpdatesTheExistingOne() {
        UUID game = game("1", "MAIN_GAME", null);
        provider.texts.put("en", Map.of("1", "English."));
        provider.texts.put("tr", Map.of("1", TURKISH));
        service.nextBatch();
        assertEquals(1, rows(game).size());
        clock.advance(LocalizedDescriptionService.RECHECK.plusHours(1));
        provider.texts.put("tr", Map.of("1", "Yeni ve güncel bir Türkçe açıklama için daha fazlası."));
        assertEquals(1, service.nextBatch());
        assertEquals(List.of("tr:Yeni ve güncel bir Türkçe açıklama için daha fazlası.:STEAM"), rows(game), "var olan satır güncellenir, ikinci satır oluşmaz");
        assertEquals(1L, jdbc.queryForObject("select count(*) from game_localized_descriptions", Long.class));
    }

    @Test void providerFailureWritesNothingAndPausesBeforeRetrying() {
        UUID game = game("1", "MAIN_GAME", null);
        provider.texts.put("en", Map.of("1", "English."));
        provider.texts.put("tr", Map.of("1", TURKISH));
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, service.nextBatch());
        assertEquals(List.of(), rows(game));
        assertNull(jdbc.queryForObject("select max(field) from game_metadata_provenance where game_id=? and field='STEAM_DESC_TR'", String.class, game));
        provider.failure = null;
        assertEquals(-1, service.nextBatch(), "duraklatma süresi dolmadan denenmez");
        clock.advance(Duration.ofHours(2));
        assertEquals(1, service.nextBatch());
        assertEquals(1, rows(game).size());
    }
}
