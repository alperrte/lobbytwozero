package com.ltz.game.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GamePriceProvider;
import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.service.GamePriceService;
import com.ltz.game.domain.exception.ProviderException;

/** Opt-in ITAD → gerçek adaptör → geçici PostgreSQL → public API kabulü; normal CI dış ağa bağımlı olmaz. */
@SpringBootTest(properties = { "ltz.game.rating.sync.enabled=false", "ltz.game.pricing.sync.enabled=false",
        "logging.level.root=ERROR" })
class ITGamePriceLivePilot extends PostgresTestSupport {

    @Autowired JdbcTemplate jdbc;
    @Autowired GamePriceService prices;
    @Autowired GamePriceProvider provider;
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

    /** Aday pazarların (ülke:para birimi) ITAD'nin gerçekten döndürdüğü para birimiyle uyuştuğunu doğrular. Sonuç target/market-pilot.txt dosyasına yazılır; anahtar ve ham yanıt yazılmaz. */
    @Test void candidateMarketsMatchTheCurrencyItadReturns() throws Exception {
        assertTrue(Boolean.getBoolean("ltz.game.live-pilot"), "Canlı pilot açıkça etkinleştirilmeli.");
        Set<UUID> ids = new java.util.LinkedHashSet<>();
        for (String steamId : new String[] { "292030", "1245620", "1091500" }) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            jdbc.update("""
                    insert into games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at)
                    values (?,'ITAD market pilot','itad market pilot','EXTERNAL','MAIN_GAME','INDEX_ONLY',now(),now())""", id);
            jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM',?)", UUID.randomUUID(), id, steamId);
        }
        String[] candidates = { "US:USD", "GB:GBP", "DE:EUR", "CA:CAD", "AU:AUD", "NZ:NZD", "PL:PLN", "BR:BRL", "JP:JPY", "KR:KRW", "CH:CHF",
                "NO:NOK", "MX:MXN", "IN:INR", "ID:IDR", "MY:MYR", "PH:PHP", "SG:SGD", "TH:THB", "TW:TWD", "VN:VND", "HK:HKD", "CN:CNY",
                "SA:SAR", "AE:AED", "QA:QAR", "KW:KWD", "IL:ILS", "ZA:ZAR", "UA:UAH", "RU:RUB", "KZ:KZT", "AR:ARS", "CL:CLP", "CO:COP",
                "PE:PEN", "UY:UYU", "CR:CRC", "TR:TRY", "TR:USD" };
        List<String> lines = new ArrayList<>();
        for (String candidate : candidates) {
            String[] parts = candidate.split(":");
            String result;
            try {
                var quotes = provider.prices(ids, new Market(parts[0], parts[1]));
                result = quotes.isEmpty() ? "NO_OFFER" : "OK offers=" + quotes.size();
            } catch (ProviderException error) {
                result = error.code();
            } catch (IllegalArgumentException _) {
                result = "INVALID_MARKET";
            }
            lines.add(candidate + " " + result);
            Thread.sleep(1200);
        }
        Files.write(Path.of("target", "market-pilot.txt"), lines);
    }
}
