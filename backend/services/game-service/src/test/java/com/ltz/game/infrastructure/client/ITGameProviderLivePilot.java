package com.ltz.game.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.service.GameDetailRefreshService;

/** Yalnız açıkça -Dtest=ITGameProviderLivePilot ile çalışır; standart testler dış ağa bağımlı olmaz. */
@SpringBootTest(properties = { "ltz.game.rating.sync.enabled=false", "logging.level.root=ERROR" })
class ITGameProviderLivePilot extends PostgresTestSupport {

    @Autowired JdbcTemplate jdbc;
    @Autowired GameDetailRefreshService refresh;
    @Autowired WebApplicationContext context;

    @Test void actualIgdbDataReachesIsolatedDatabaseAndPublicGameApi() throws Exception {
        assertTrue(Boolean.getBoolean("ltz.game.live-pilot"), "Canlı pilot açıkça etkinleştirilmeli.");
        assertTrue(System.getenv("IGDB_CLIENT_ID") != null && System.getenv("IGDB_CLIENT_SECRET") != null,
                "IGDB kimlik bilgileri süreç ortamında gerekli.");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at)
                values (?,'Dota 2','dota 2','EXTERNAL','MAIN_GAME','INDEX_ONLY',now(),now())""", id);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM','570')",
                UUID.randomUUID(), id);
        assertEquals("REFRESHED", refresh.refresh(id), "Gerçek IGDB yenilemesi tamamlanmalı.");
        assertEquals("FRESH", refresh.refresh(id), "TTL içindeki ikinci istek sağlayıcıyı yeniden çağırmamalı.");
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build().perform(get("/api/v1/game/games/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.metadata.provenance[?(@.source == 'IGDB')]").isNotEmpty())
                .andExpect(jsonPath("$.metadata.languageSupport").isNotEmpty());
    }
}
