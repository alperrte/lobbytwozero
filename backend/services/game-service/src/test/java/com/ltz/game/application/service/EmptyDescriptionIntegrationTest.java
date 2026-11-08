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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Boş açıklama yedeği: yalnız boş açıklama dolar; mevcut/yönetici metni ve manuel kilit ezilmez; başka dildeki metin "İngilizce" yazılmaz; hata duraklatır. */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
@Import(EmptyDescriptionIntegrationTest.Fakes.class)
class EmptyDescriptionIntegrationTest extends PostgresTestSupport {
    @TestConfiguration static class Fakes {
        @Bean @Primary EnglishProvider englishProvider() { return new EnglishProvider(); }
        @Bean @Primary MutableClock englishClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class EnglishProvider implements GameProvider {
        final Map<String, String> texts = new HashMap<>();
        final List<List<String>> queries = new CopyOnWriteArrayList<>();
        final List<String> languages = new CopyOnWriteArrayList<>();
        String failure;
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { throw new ProviderException("UNSUPPORTED_CAPABILITY"); }
        @Override public Map<String, String> shortDescriptions(java.util.Collection<String> ids, String language) {
            queries.add(List.copyOf(ids));
            languages.add(language);
            if (failure != null) throw new ProviderException(failure);
            Map<String, String> found = new HashMap<>();
            ids.forEach(id -> { if (texts.containsKey(id)) found.put(id, texts.get(id)); });
            return found;
        }
    }

    @Autowired EnglishProvider provider;
    @Autowired MutableClock clock;
    @Autowired EmptyDescriptionService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;

    @BeforeEach void reset() {
        clock.now = Instant.parse("2026-11-08T00:00:00Z");
        provider.texts.clear(); provider.queries.clear(); provider.languages.clear(); provider.failure = null;
        service.resetForTests();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }

    private UUID game(String appId, String type, String description) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,description,created_at,updated_at) values (?,?,?,'EXTERNAL',?,?,now(),now())", id, "G" + appId, "g" + appId, type, description);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id,last_synced_at) values (?,?,'STEAM',?,now())", UUID.randomUUID(), id, appId);
        return id;
    }

    private String descriptionOf(UUID game) { return jdbc.queryForObject("select description from games where id=?", String.class, game); }

    @Test void onlyEmptyDescriptionsAreFilledWithEnglishSteamTextAndEverythingElseIsPreserved() throws Exception {
        UUID empty = game("1", "MAIN_GAME", null), blank = game("2", "MAIN_GAME", "   "), existing = game("3", "MAIN_GAME", "Existing IGDB summary.");
        UUID foreign = game("4", "MAIN_GAME", null), noText = game("5", "MAIN_GAME", null), locked = game("6", "MAIN_GAME", null), dlc = game("7", "DLC", null);
        jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at,version) values (?,?,'DESCRIPTION',?,now(),0)", UUID.randomUUID(), locked, UUID.randomUUID());
        provider.texts.putAll(Map.of("1", "A story about the end of the world and all of its people.", "2", "Another text for you to read in the dark.", "3", "Steam text that must not replace the existing one in the game.",
                "4", "这是一个关于在岛屿上寻找一百只可爱动物的冒险游戏", "6", "Locked description that is the text of an admin field.", "7", "Dlc text for the dlc and the game."));

        assertEquals(5, service.nextBatch());

        assertEquals("A story about the end of the world and all of its people.", descriptionOf(empty));
        assertEquals("Another text for you to read in the dark.", descriptionOf(blank), "yalnız boşluktan oluşan açıklama boş sayılır");
        assertEquals("Existing IGDB summary.", descriptionOf(existing), "mevcut açıklama değişmez (aday bile değildir)");
        assertNull(descriptionOf(foreign), "başka dildeki metin İngilizce diye yazılmaz");
        assertNull(descriptionOf(noText), "Steam metin vermezse metin uydurulmaz");
        assertNull(descriptionOf(locked), "manuel DESCRIPTION kilidi korunur");
        assertNull(descriptionOf(dlc), "ana oyun olmayan kayıt işlenmez");
        assertEquals("STEAM", jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='DESCRIPTION'", String.class, empty));
        assertEquals("AVAILABLE", jdbc.queryForObject("select data_state from game_metadata_provenance where game_id=? and field='STEAM_DESC_EN'", String.class, empty));
        assertEquals("EMPTY", jdbc.queryForObject("select data_state from game_metadata_provenance where game_id=? and field='STEAM_DESC_EN'", String.class, foreign));
        assertEquals(List.of("en"), provider.languages, "yalnız İngilizce istenir");
        assertEquals(List.of(List.of("1", "2", "4", "5", "6")), provider.queries.stream().map(q -> q.stream().sorted().toList()).toList(), "dolu açıklamalı ve ana oyun olmayan kayıt sorulmaz");
        assertEquals(-1, service.nextBatch(), "işlenen kayıt yeniden sorulmaz (idempotent)");
        assertEquals(1, provider.queries.size());

        // API: Steam kaynaklı metin İngilizce olarak işaretlenir
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/game/games/" + empty).param("lang", "tr")).andExpect(jsonPath("$.descriptionLanguage").value("en"))
                .andExpect(jsonPath("$.description").value("A story about the end of the world and all of its people."));
    }

    @Test void providerFailureWritesNothingAndPausesBeforeRetrying() {
        UUID game = game("1", "MAIN_GAME", null);
        provider.texts.put("1", "A story about the end of the world and all of its people.");
        provider.failure = "RATE_LIMITED";
        assertEquals(-1, service.nextBatch());
        assertNull(descriptionOf(game));
        provider.failure = null;
        assertEquals(-1, service.nextBatch(), "duraklatma süresi dolmadan denenmez");
        clock.advance(Duration.ofHours(2));
        assertEquals(1, service.nextBatch());
        assertEquals("A story about the end of the world and all of its people.", descriptionOf(game));
    }

    @Test void aDescriptionWrittenByAnotherPathAfterTheCandidateQueryIsNeverOverwritten() {
        UUID game = game("1", "MAIN_GAME", null);
        provider.texts.put("1", "A story about the end of the world and all of its people.");
        service.nextBatch();
        jdbc.update("update games set description='Admin wrote this.' where id=?", game);
        clock.advance(EmptyDescriptionService.RECHECK.plusHours(1));
        assertEquals(-1, service.nextBatch(), "açıklaması artık dolu oyun aday değildir");
        assertEquals("Admin wrote this.", descriptionOf(game));
    }
}
