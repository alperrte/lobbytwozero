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
import com.ltz.game.application.service.GamePriceService;

/** Opt-in ITAD → gerçek adaptör → geçici PostgreSQL → public API kabulü; normal CI dış ağa bağımlı olmaz. */
@SpringBootTest(properties = { "ltz.game.rating.sync.enabled=false", "ltz.game.pricing.sync.enabled=false",
        "logging.level.root=ERROR" })
class ITGamePriceLivePilot extends PostgresTestSupport {

    @Autowired JdbcTemplate jdbc;
    @Autowired GamePriceService prices;
    @Autowired WebApplicationContext context;

    @Test void actualItadPricesReachIsolatedDatabaseAndPublicGameApi() throws Exception {
        assertTrue(Boolean.getBoolean("ltz.game.live-pilot"), "Canlı pilot açıkça etkinleştirilmeli.");
        assertTrue(System.getenv("ITAD_API_KEY") != null && !System.getenv("ITAD_API_KEY").isBlank(),
                "ITAD anahtarı süreç ortamında gerekli.");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at)
                values (?,'ITAD pilot','itad pilot','EXTERNAL','MAIN_GAME','INDEX_ONLY',now(),now())""", id);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM','1086940')",
                UUID.randomUUID(), id);
        prices.refresh();
        String state = jdbc.queryForObject("select status from game_price_refresh_state where game_id=?", String.class, id);
        String error = jdbc.queryForObject("select error_code from game_price_refresh_state where game_id=?", String.class, id);
        assertEquals("AVAILABLE", state, "Gerçek fiyat yayımlanmalı; güvenli hata kodu: " + error);
        Long version = jdbc.queryForObject("select version from game_price_refresh_state where game_id=?", Long.class, id);
        prices.refresh();
        assertEquals(version, jdbc.queryForObject("select version from game_price_refresh_state where game_id=?", Long.class, id),
                "TTL içindeki ikinci tur fiyatı yeniden çekmemeli.");
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(get("/api/v1/game/games/prices").param("gameIds", id.toString()).param("country", "TR").param("currency", "TRY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.market.country").value("TR"))
                .andExpect(jsonPath("$.market.currency").value("TRY"))
                .andExpect(jsonPath("$.items[0].gameId").value(id.toString()))
                .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.items[0].offers[0].source").value("ITAD"))
                .andExpect(jsonPath("$.items[0].offers[0].url").isNotEmpty())
                .andExpect(jsonPath("$.items[0].offers[0].expiresAt").isNotEmpty());
    }
}
