package com.ltz.lfp.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.ltz.lfp.IntegrationTestSupport;

/** LTZ-UMT-LFP-01: oyun arkadaşı ilanı oluşturma (LFP-01..09) ve süre seçimi (LFP-15). */
class ListingCreateTest extends IntegrationTestSupport {

    // ---------------------------------------------------------------- LFP-01..09 mutlu yol

    @Test
    void tumAlanlarlaIlanAcilir201SahipTokendenAlinir() throws Exception {
        UUID owner = newUser();
        UUID gameId = games.addGame("Valorant");
        Instant start = Instant.now().plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> body = listingBody(gameId);
        body.put("platform", "PLAYSTATION");
        body.put("skillLevel", "  Diamond+  ");
        body.put("roles", List.of("Duelist", "Controller"));
        body.put("playersNeeded", 3);
        body.put("playTimeStart", start.toString());
        body.put("playTimeEnd", start.plus(Duration.ofHours(3)).toString());
        body.put("micRequired", true);
        body.put("durationHours", 48);

        String response = createListing(owner, body)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(LfpApiPaths.LISTINGS + "/")))
                .andExpect(jsonPath("$.ownerId").value(owner.toString()))
                .andExpect(jsonPath("$.gameId").value(gameId.toString()))
                .andExpect(jsonPath("$.gameTitle").value("Valorant"))
                .andExpect(jsonPath("$.platform").value("PLAYSTATION"))
                .andExpect(jsonPath("$.skillLevel").value("Diamond+"))
                .andExpect(jsonPath("$.roles[0]").value("Duelist"))
                .andExpect(jsonPath("$.roles[1]").value("Controller"))
                .andExpect(jsonPath("$.playersNeeded").value(3))
                .andExpect(jsonPath("$.playTimeStart").value(start.toString()))
                .andExpect(jsonPath("$.micRequired").value(true))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Instant createdAt = Instant.parse(JsonPath.read(response, "$.createdAt"));
        Instant expiresAt = Instant.parse(JsonPath.read(response, "$.expiresAt"));
        assertThat(Duration.between(createdAt, expiresAt)).isEqualTo(Duration.ofHours(48));
        assertThat(listingCount(owner)).isEqualTo(1);
        assertThat(jdbc.queryForList("select role from lfp_listing_roles where listing_id = ? order by position",
                String.class, UUID.fromString(JsonPath.read(response, "$.id")))).containsExactly("Duelist", "Controller");
    }

    @Test
    void seviyeVeOyunZamaniOpsiyonelVarsayilanSure24Saat() throws Exception {
        UUID owner = newUser();

        String response = createListing(owner, listingBody(games.addGame("Deep Rock Galactic")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skillLevel").doesNotExist())
                .andExpect(jsonPath("$.playTimeStart").doesNotExist())
                .andExpect(jsonPath("$.playTimeEnd").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Instant createdAt = Instant.parse(JsonPath.read(response, "$.createdAt"));
        Instant expiresAt = Instant.parse(JsonPath.read(response, "$.expiresAt"));
        assertThat(Duration.between(createdAt, expiresAt)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void bosSeviyeBelirtilmediSayilir() throws Exception {
        Map<String, Object> body = listingBody(games.addGame("Apex Legends"));
        body.put("skillLevel", "   ");

        createListing(newUser(), body).andExpect(status().isCreated()).andExpect(jsonPath("$.skillLevel").doesNotExist());
    }

    @Test
    void tumPlatformlarKabulEdilirBilinmeyenPlatform400() throws Exception {
        UUID owner = newUser();
        UUID gameId = games.addGame("Rocket League");
        for (String platform : List.of("PC", "PLAYSTATION", "XBOX", "NINTENDO_SWITCH", "MOBILE")) {
            Map<String, Object> body = listingBody(gameId);
            body.put("platform", platform);
            createListing(owner, body).andExpect(status().isCreated()).andExpect(jsonPath("$.platform").value(platform));
        }

        Map<String, Object> body = listingBody(gameId);
        body.put("platform", "SEGA_SATURN");
        createListing(newUser(), body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void ayniRolBuyukKucukHarfFarkiylaBirKezTutulurSiraKorunur() throws Exception {
        Map<String, Object> body = listingBody(games.addGame("Overwatch 2"));
        body.put("roles", List.of(" Tank ", "Healer", "tank", "DPS"));

        createListing(newUser(), body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles.length()").value(3))
                .andExpect(jsonPath("$.roles[0]").value("Tank"))
                .andExpect(jsonPath("$.roles[1]").value("Healer"))
                .andExpect(jsonPath("$.roles[2]").value("DPS"));
    }

    @Test
    void aciklamadaSatirSonuVeEmojiKorunur() throws Exception {
        Map<String, Object> body = listingBody(games.addGame("Minecraft"));
        body.put("description", "Survival sunucu 👨\u200D👩\u200D👧\r\nAkşam 21:00 sonrası\tsakin oyun");

        createListing(newUser(), body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Survival sunucu 👨\u200D👩\u200D👧\nAkşam 21:00 sonrası\tsakin oyun"));
    }

    // ---------------------------------------------------------------- kimlik ve sahiplik

    @Test
    void tokensiz401KayitYok() throws Exception {
        UUID gameId = games.addGame("Dota 2");

        mockMvc.perform(post(LfpApiPaths.LISTINGS).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(listingBody(gameId))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(jdbc.queryForObject("select count(*) from lfp_listings where game_id = ?", Integer.class, gameId))
                .isZero();
    }

    @Test
    void sahipGovdedenAlinmazBilinmeyenAlan400() throws Exception {
        UUID real = newUser();
        UUID forged = newUser();
        Map<String, Object> body = listingBody(games.addGame("CS2"));
        body.put("ownerId", forged.toString());

        createListing(real, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        Map<String, Object> withStatus = listingBody(games.addGame("CS2"));
        withStatus.put("status", "CLOSED");
        createListing(real, withStatus).andExpect(status().isBadRequest());

        assertThat(listingCount(real)).isZero();
        assertThat(listingCount(forged)).isZero();
    }

    // ---------------------------------------------------------------- LFP-02 oyun doğrulaması

    @Test
    void katalogdaOlmayanOyun422KayitYok() throws Exception {
        UUID owner = newUser();

        createListing(owner, listingBody(UUID.randomUUID())).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));

        assertThat(listingCount(owner)).isZero();
    }

    @Test
    void oyunOlmayanKatalogKaydi422BilinmeyenTurKabul() throws Exception {
        UUID owner = newUser();
        for (String type : List.of("SOUNDTRACK", "VIDEO", "HARDWARE", "SOFTWARE")) {
            createListing(owner, listingBody(games.addGame("Not a game", type)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("GAME_NOT_PLAYABLE"));
        }
        for (String type : List.of("MAIN_GAME", "DLC", "DEMO", "UNKNOWN")) {
            createListing(owner, listingBody(games.addGame("Game " + type, type))).andExpect(status().isCreated());
        }
        assertThat(listingCount(owner)).isEqualTo(4);
    }

    @Test
    void katalogUlasilamazsa503FailClosedKayitYok() throws Exception {
        UUID owner = newUser();
        UUID gameId = games.addGame("Elden Ring");
        games.setUnavailable(true);

        createListing(owner, listingBody(gameId)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_CATALOG_UNAVAILABLE"));

        assertThat(listingCount(owner)).isZero();
    }

    @Test
    void gecersizGovdeOyunKataloguCagrilmadanReddedilir() throws Exception {
        Map<String, Object> body = listingBody(games.addGame("Halo"));
        body.put("description", "   ");

        createListing(newUser(), body).andExpect(status().isBadRequest());

        assertThat(games.calls()).isZero();
    }

    // ---------------------------------------------------------------- alan doğrulamaları

    @Test
    void zorunluAlanlarEksikse400AlanAdiDonerDegerYansitilmaz() throws Exception {
        createListingJson(newUser(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'gameId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'platform')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'roles')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'playersNeeded')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'micRequired')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists());
    }

    @Test
    void oyuncuSayisi1ile50Arasi() throws Exception {
        UUID gameId = games.addGame("Sea of Thieves");
        for (int invalid : new int[] {0, -1, 51}) {
            Map<String, Object> body = listingBody(gameId);
            body.put("playersNeeded", invalid);
            createListing(newUser(), body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        for (int valid : new int[] {1, 50}) {
            Map<String, Object> body = listingBody(gameId);
            body.put("playersNeeded", valid);
            createListing(newUser(), body).andExpect(status().isCreated());
        }
    }

    @Test
    void rolKurallariBosAltidanFazlaUzunKontrolKarakteri400() throws Exception {
        UUID gameId = games.addGame("League of Legends");
        List<List<String>> invalid = List.of(
                List.of(),
                List.of("a", "b", "c", "d", "e", "f"),
                List.of("   "),
                List.of("x".repeat(31)),
                List.of("Mid\nlane"));
        for (List<String> roles : invalid) {
            Map<String, Object> body = listingBody(gameId);
            body.put("roles", roles);
            createListing(newUser(), body).andExpect(status().isBadRequest());
        }
        Map<String, Object> body = listingBody(gameId);
        body.put("roles", List.of("x".repeat(30), "Jungle", "Mid", "Top", "Support"));
        createListing(newUser(), body).andExpect(status().isCreated());
    }

    @Test
    void aciklamaKurallariBosUzunKontrolKarakteri400() throws Exception {
        UUID gameId = games.addGame("Fortnite");
        for (String description : List.of("", "   \n  ", "x".repeat(1001), "zil\u0007sesi", "gizli\u200Byazi")) {
            Map<String, Object> body = listingBody(gameId);
            body.put("description", description);
            createListing(newUser(), body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.oneOf("INVALID_DESCRIPTION",
                            "VALIDATION_FAILED")));
        }
        Map<String, Object> body = listingBody(gameId);
        body.put("description", "x".repeat(1000));
        createListing(newUser(), body).andExpect(status().isCreated());
    }

    @Test
    void seviyeTekSatirVeEnFazla50Karakter() throws Exception {
        UUID gameId = games.addGame("Rainbow Six Siege");
        for (String skill : List.of("x".repeat(51), "Gold\nPlat")) {
            Map<String, Object> body = listingBody(gameId);
            body.put("skillLevel", skill);
            createListing(newUser(), body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SKILL_LEVEL"));
        }
    }

    // ---------------------------------------------------------------- LFP-07 oyun zamanı ve LFP-15 süre

    @Test
    void oyunZamaniKurallari400() throws Exception {
        UUID gameId = games.addGame("Destiny 2");
        Instant now = Instant.now();
        Map<String, Instant[]> invalid = Map.of(
                "gecmis", new Instant[] {now.minus(Duration.ofHours(1)), null},
                "bitisBaslangictanOnce", new Instant[] {now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(1))},
                "bitisBaslangicaEsit", new Instant[] {now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(2))},
                "aralik24SaattenUzun", new Instant[] {now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(26))},
                "baslangicsizBitis", new Instant[] {null, now.plus(Duration.ofHours(2))});
        for (Map.Entry<String, Instant[]> scenario : invalid.entrySet()) {
            Map<String, Object> body = listingBody(gameId);
            putTime(body, "playTimeStart", scenario.getValue()[0]);
            putTime(body, "playTimeEnd", scenario.getValue()[1]);
            createListing(newUser(), body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PLAY_TIME"));
        }
    }

    @Test
    void oyunZamaniIlanBittiktenSonraOlamaz() throws Exception {
        Map<String, Object> body = listingBody(games.addGame("Warframe"));
        body.put("durationHours", 2);
        putTime(body, "playTimeStart", Instant.now().plus(Duration.ofHours(3)));

        createListing(newUser(), body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAY_TIME_AFTER_EXPIRY"));
    }

    @Test
    void sureSinirlari1ile168Saat() throws Exception {
        UUID gameId = games.addGame("Path of Exile 2");
        for (int hours : new int[] {0, 169, -5}) {
            Map<String, Object> body = listingBody(gameId);
            body.put("durationHours", hours);
            createListing(newUser(), body).andExpect(status().isBadRequest());
        }
        for (int hours : new int[] {1, 168}) {
            Map<String, Object> body = listingBody(gameId);
            body.put("durationHours", hours);
            String response = createListing(newUser(), body).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Instant createdAt = Instant.parse(JsonPath.read(response, "$.createdAt"));
            Instant expiresAt = Instant.parse(JsonPath.read(response, "$.expiresAt"));
            assertThat(Duration.between(createdAt, expiresAt)).isEqualTo(Duration.ofHours(hours));
        }
    }

    // ---------------------------------------------------------------- kötüye kullanım sınırları

    @Test
    void ayniAndaEnFazla5AcikIlanAltinci409() throws Exception {
        UUID owner = newUser();
        for (int i = 0; i < 5; i++) {
            openListing(owner);
        }

        createListing(owner, listingBody(games.addGame("Sixth"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPEN_LISTING_LIMIT"));

        assertThat(listingCount(owner)).isEqualTo(5);
        // Başka kullanıcı etkilenmez.
        openListing(newUser());
    }

    @Test
    void suresiGecmisAmaIsaretlenmemisIlanAcikSayilmaz() throws Exception {
        UUID owner = newUser();
        for (int i = 0; i < 5; i++) {
            openListing(owner);
        }
        UUID stale = jdbc.queryForObject("select id from lfp_listings where owner_id = ? limit 1", UUID.class, owner);
        jdbc.update("update lfp_listings set created_at = created_at - interval '2 hours', "
                + "expires_at = created_at - interval '1 hour' where id = ?", stale);

        createListing(owner, listingBody(games.addGame("Replacement"))).andExpect(status().isCreated());
    }

    @Test
    void saatteEnFazla10YeniIlanKapatilanlarDaSayilir429() throws Exception {
        UUID owner = newUser();
        for (int i = 0; i < 10; i++) {
            openListing(owner);
            // Açık ilan sınırına takılmadan saatlik sınırı sınamak için ilan kapatılmış sayılır.
            jdbc.update("update lfp_listings set status = 'CLOSED', closed_at = now() where owner_id = ? "
                    + "and status = 'OPEN'", owner);
        }

        createListing(owner, listingBody(games.addGame("Eleventh"))).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LISTING_RATE_LIMITED"));

        assertThat(listingCount(owner)).isEqualTo(10);
    }

    private static void putTime(Map<String, Object> body, String field, Instant value) {
        if (value != null) {
            body.put(field, value.toString());
        }
    }
}
