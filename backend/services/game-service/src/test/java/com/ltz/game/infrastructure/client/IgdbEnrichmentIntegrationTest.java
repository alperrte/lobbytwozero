package com.ltz.game.infrastructure.client;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.service.GameDetailRefreshService;
import com.ltz.game.infrastructure.client.FakeServer.Reply;
import com.ltz.game.infrastructure.config.GameRatingConfiguration;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gerçek IGDB adaptörü → yenileme → PostgreSQL → public API. Yalnız dış sağlayıcı HTTP sunucusu sahtedir. */
@SpringBootTest(properties = "ltz.game.rating.sync.enabled=false")
@Import(IgdbEnrichmentIntegrationTest.ProviderFixture.class)
class IgdbEnrichmentIntegrationTest extends PostgresTestSupport {
    @TestConfiguration
    static class ProviderFixture {
        @Bean(destroyMethod = "close") FakeServer igdbFixture() throws IOException { return new FakeServer(); }

        @Bean @Primary
        GameProvider integratedProvider(@Qualifier("steamGameProvider") GameProvider steam, FakeServer api) {
            var client = GameRatingConfiguration.restClient(RestClient.builder(), api.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2));
            var source = new IgdbDetailSource(client, Clock.systemUTC(), "fixture-id", "fixture-secret",
                    api.baseUrl() + "/v4", api.baseUrl() + "/token", Duration.ZERO);
            return new DetailEnrichedGameProvider(steam, source);
        }
    }

    @Autowired FakeServer api;
    @Autowired JdbcTemplate jdbc;
    @Autowired GameDetailRefreshService refresh;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    UUID id;

    @BeforeEach void seed() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        id = UUID.randomUUID();
        jdbc.update("""
                insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at)
                values (?,'Fixture Game','fixture game','EXTERNAL','MAIN_GAME','INDEX_ONLY',now(),now())""", id);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM','570')", UUID.randomUUID(), id);
    }

    private void details(String summary) {
        // İlk istekte token ve kaynak çözülür; sonraki isteklerde önbellek kullanılır.
        int before = api.received().size();
        if (before == 0) api.enqueue(Reply.json(200, "{\"access_token\":\"fixture-token\",\"expires_in\":5000000,\"token_type\":\"bearer\"}"),
                Reply.json(200, "[{\"id\":1,\"name\":\"Steam\"}]"));
        api.enqueue(Reply.json(200, "[{\"game\":777,\"uid\":\"570\"}]"), Reply.json(200, """
                [{"id":777,"summary":"%s","first_release_date":1373241600,
                  "language_supports":[{"language":{"locale":"tr-TR"},"language_support_type":{"name":"Subtitles"}}]}]
                """.formatted(summary)));
    }

    @Test void adapterPersistsPartialDetailsAndPublicReadsDoNotCallTheProvider() throws Exception {
        details("Fixture description");
        assertEquals("REFRESHED", refresh.refresh(id));
        int calls = api.received().size();
        assertEquals("FRESH", refresh.refresh(id));
        mvc.perform(get("/api/v1/game/games/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Fixture description"))
                .andExpect(jsonPath("$.game.origin").value("EXTERNAL"))
                .andExpect(jsonPath("$.game.metadataCompleteness").value("PARTIAL"))
                .andExpect(jsonPath("$.metadata.languageSupport[0].languageCode").value("tr-tr"))
                .andExpect(jsonPath("$.metadata.languageSupport[0].subtitleSupport").value("SUPPORTED"))
                .andExpect(jsonPath("$.metadata.languageSupport[0].interfaceSupport").value("UNKNOWN"))
                .andExpect(jsonPath("$.metadata.provenance[?(@.field == 'LOCALIZATION')].source").value(org.hamcrest.Matchers.hasItem("IGDB")));
        assertEquals(calls, api.received().size(), "TTL ve public GET sağlayıcıya yeni istek atmaz");
        assertEquals(1, jdbc.queryForObject("select count(*) from game_external_refs where game_id=?", Integer.class, id));
    }

    @Test void manualOverridesSurviveRealAdapterRefreshAndOutagePreservesLastSnapshot() throws Exception {
        jdbc.update("update games set description='Manual description' where id=?", id);
        for (String field : new String[]{"DESCRIPTION", "LOCALIZATION"})
            jdbc.update("insert into game_field_overrides(id,game_id,field,actor_id,created_at) values (?,?,?,?,now())",
                    UUID.randomUUID(), id, field, UUID.randomUUID());
        details("Provider description");
        assertEquals("REFRESHED", refresh.refresh(id));
        mvc.perform(get("/api/v1/game/games/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Manual description"))
                .andExpect(jsonPath("$.metadata.languageSupport").isEmpty());
        jdbc.update("update games set details_expires_at=now()-interval '1 second' where id=?", id);
        api.enqueue(Reply.json(503, "{}"));
        assertEquals("FAILED", refresh.refresh(id));
        mvc.perform(get("/api/v1/game/games/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Manual description"));
        assertEquals("BACKOFF", refresh.refresh(id));
    }
}
