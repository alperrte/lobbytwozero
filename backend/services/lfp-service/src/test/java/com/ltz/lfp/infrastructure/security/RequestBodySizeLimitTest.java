package com.ltz.lfp.infrastructure.security;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ltz.lfp.IntegrationTestSupport;

class RequestBodySizeLimitTest extends IntegrationTestSupport {

    private static final int OVER_LIMIT_BYTES = 9 * 1024;

    @Test
    void sinirinUstundekiGovde413ProblemDetailKayitYok() throws Exception {
        var owner = newUser();
        Map<String, Object> body = listingBody(games.addGame("Big"));
        body.put("description", "a".repeat(OVER_LIMIT_BYTES));

        createListing(owner, body)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        org.assertj.core.api.Assertions.assertThat(listingCount(owner)).isZero();
    }

    @Test
    void enBuyukGecerliIlanSinirIcindedir() throws Exception {
        Map<String, Object> body = listingBody(games.addGame("Max"));
        // Çok baytlı (UTF-8'de 2 bayt) karakterlerle en uzun açıklama, seviye ve roller 8 KB'nin altında kalır.
        body.put("description", "ğ".repeat(1000));
        body.put("skillLevel", "ş".repeat(50));
        body.put("roles", java.util.List.of("ç".repeat(30), "ö".repeat(30), "ü".repeat(30), "ı".repeat(30),
                "İ".repeat(30)));

        createListing(newUser(), body).andExpect(status().isCreated());
    }
}
