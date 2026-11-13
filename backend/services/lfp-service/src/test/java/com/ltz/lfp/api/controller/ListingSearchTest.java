package com.ltz.lfp.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.jayway.jsonpath.JsonPath;
import com.ltz.lfp.IntegrationTestSupport;

/**
 * LFP-10 aktif ilan listesi, LFP-11 arama/süzgeç, sahibin kendi ilanları ve detay. Paylaşılan veritabanında testler
 * birbirini etkilemesin diye her test kendi oyununa göre süzer.
 */
class ListingSearchTest extends IntegrationTestSupport {

    // ---------------------------------------------------------------- LFP-10

    @Test
    void aktifIlanlarEnYeniOnceListelenirKapaliVeSuresiDolmusGorunmez() throws Exception {
        UUID gameId = games.addGame("Lethal Company");
        UUID first = openListing(newUser(), listingBody(gameId));
        UUID second = openListing(newUser(), listingBody(gameId));
        UUID closed = openListing(newUser(), listingBody(gameId));
        UUID overdue = openListing(newUser(), listingBody(gameId));
        UUID marked = openListing(newUser(), listingBody(gameId));
        jdbc.update("update lfp_listings set status = 'CLOSED', closed_at = now() where id = ?", closed);
        makeOverdue(overdue);
        makeOverdue(marked);
        jdbc.update("update lfp_listings set status = 'EXPIRED' where id = ?", marked);

        search(newUser(), "?gameId=" + gameId).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second.toString()))
                .andExpect(jsonPath("$.items[1].id").value(first.toString()))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void sayfalamaImlecleIlerlerTekrarVeAtlamaYok() throws Exception {
        UUID gameId = games.addGame("Phasmophobia");
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(0, openListing(newUser(), listingBody(gameId)).toString());
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String query = "?gameId=" + gameId + "&limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            String body = search(newUser(), query).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            seen.addAll(JsonPath.<List<String>>read(body, "$.items[*].id"));
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).containsExactlyElementsOf(created);
    }

    @Test
    void sayfaBoyutuVeImlecDogrulanir() throws Exception {
        UUID viewer = newUser();
        search(viewer, "?limit=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
        search(viewer, "?limit=51").andExpect(status().isBadRequest());
        search(viewer, "?limit=50").andExpect(status().isOk());
        search(viewer, "?cursor=bozuk!!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        search(viewer, "?cursor=" + "A".repeat(200)).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- LFP-11 süzgeçler

    @Test
    void platformVeMikrofonSuzgeci() throws Exception {
        UUID gameId = games.addGame("Fall Guys");
        UUID pcMic = openListing(newUser(), body(gameId, "PC", true));
        openListing(newUser(), body(gameId, "PC", false));
        UUID psMic = openListing(newUser(), body(gameId, "PLAYSTATION", true));

        search(newUser(), "?gameId=" + gameId + "&platform=PC&micRequired=true")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(pcMic.toString()));
        search(newUser(), "?gameId=" + gameId + "&platform=PLAYSTATION")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(psMic.toString()));
        search(newUser(), "?platform=DREAMCAST").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void rolSuzgeciBuyukKucukHarfeDuyarsizTamEslesme() throws Exception {
        UUID gameId = games.addGame("Overwatch");
        Map<String, Object> tank = listingBody(gameId);
        tank.put("roles", List.of("Main Tank", "Support"));
        UUID tankListing = openListing(newUser(), tank);
        Map<String, Object> dps = listingBody(gameId);
        dps.put("roles", List.of("DPS"));
        openListing(newUser(), dps);

        search(newUser(), "?gameId=" + gameId + "&role=" + encode("main tank"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(tankListing.toString()));
        search(newUser(), "?gameId=" + gameId + "&role=Tank").andExpect(jsonPath("$.items.length()").value(0));
        search(newUser(), "?gameId=" + gameId + "&role=SUPPORT").andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void seviyeSuzgeciMetinIcerirSeviyesizIlanlarDaEslesir() throws Exception {
        UUID gameId = games.addGame("Valorant Ranked");
        Map<String, Object> diamond = listingBody(gameId);
        diamond.put("skillLevel", "Diamond 2+");
        UUID diamondListing = openListing(newUser(), diamond);
        Map<String, Object> iron = listingBody(gameId);
        iron.put("skillLevel", "Iron");
        openListing(newUser(), iron);
        UUID anyLevel = openListing(newUser(), listingBody(gameId));

        String body = search(newUser(), "?gameId=" + gameId + "&skillLevel=diamond")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id"))
                .containsExactlyInAnyOrder(diamondListing.toString(), anyLevel.toString());
    }

    @Test
    void zamanSuzgeciBaslangicAraligindakileriDonerZamansizlariDonmez() throws Exception {
        UUID gameId = games.addGame("Raid Night");
        Instant base = Instant.now().plus(Duration.ofHours(3)).truncatedTo(ChronoUnit.SECONDS);
        UUID early = openListing(newUser(), timed(gameId, base));
        UUID late = openListing(newUser(), timed(gameId, base.plus(Duration.ofHours(5))));
        openListing(newUser(), listingBody(gameId));

        search(newUser(), "?gameId=" + gameId + "&playFrom=" + base.minusSeconds(60) + "&playTo="
                + base.plusSeconds(60))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(early.toString()));
        search(newUser(), "?gameId=" + gameId + "&playFrom=" + base.plus(Duration.ofHours(1)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(late.toString()));
        search(newUser(), "?gameId=" + gameId + "&playTo=" + base.plus(Duration.ofHours(10)))
                .andExpect(jsonPath("$.items.length()").value(2));
        search(newUser(), "?playFrom=" + base.plusSeconds(60) + "&playTo=" + base)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        search(newUser(), "?playFrom=yarin").andExpect(status().isBadRequest());
    }

    @Test
    void metinAramasiOyunAdindaVeAciklamadaJokerKarakterlerKacislanir() throws Exception {
        UUID gameId = games.addGame("Hollow Knight Silksong");
        Map<String, Object> discount = listingBody(gameId);
        discount.put("description", "100% tamamlama koşusu, yardımlaşma");
        UUID percent = openListing(newUser(), discount);
        Map<String, Object> plain = listingBody(gameId);
        plain.put("description", "Sakin keşif");
        UUID other = openListing(newUser(), plain);

        search(newUser(), "?gameId=" + gameId + "&q=" + encode("100%"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(percent.toString()));
        search(newUser(), "?gameId=" + gameId + "&q=" + encode("%")).andExpect(jsonPath("$.items.length()").value(1));
        search(newUser(), "?gameId=" + gameId + "&q=" + encode("_")).andExpect(jsonPath("$.items.length()").value(0));
        search(newUser(), "?gameId=" + gameId + "&q=SILKSONG").andExpect(jsonPath("$.items.length()").value(2));
        search(newUser(), "?gameId=" + gameId + "&q=" + encode("keşif"))
                .andExpect(jsonPath("$.items[0].id").value(other.toString()));
        search(newUser(), "?q=" + "x".repeat(101)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
    }

    @Test
    void suzgeclerBirlikteUygulanir() throws Exception {
        UUID gameId = games.addGame("Helldivers 2");
        Map<String, Object> match = body(gameId, "PC", true);
        match.put("roles", List.of("Demolition"));
        match.put("skillLevel", "Veteran");
        UUID target = openListing(newUser(), match);
        Map<String, Object> wrongRole = body(gameId, "PC", true);
        wrongRole.put("skillLevel", "Veteran");
        openListing(newUser(), wrongRole);

        search(newUser(), "?gameId=" + gameId + "&platform=PC&micRequired=true&role=demolition&skillLevel=vet")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(target.toString()));
    }

    // ---------------------------------------------------------------- sahibin ilanları ve detay

    @Test
    void kendiIlanlarimTumDurumlarlaDurumSuzgeciOkumaAnindakiDurumaGore() throws Exception {
        UUID owner = newUser();
        UUID open = openListing(owner);
        UUID closed = openListing(owner);
        UUID overdue = openListing(owner);
        openListing(newUser());
        closeListing(owner, closed).andExpect(status().isOk());
        makeOverdue(overdue);

        mine(owner, "").andExpect(jsonPath("$.items.length()").value(3));
        mine(owner, "?status=OPEN").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(open.toString()));
        mine(owner, "?status=CLOSED").andExpect(jsonPath("$.items[0].id").value(closed.toString()));
        mine(owner, "?status=EXPIRED").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(overdue.toString()))
                .andExpect(jsonPath("$.items[0].status").value("EXPIRED"));
        mine(newUser(), "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void detayHerDurumdaGorunurSuresiGecmisIlanExpiredDoner() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);

        getListing(newUser(), listing).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(listing.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").isNumber());

        makeOverdue(listing);
        getListing(newUser(), listing).andExpect(jsonPath("$.status").value("EXPIRED"));
        assertThat(listingStatus(listing)).isEqualTo("OPEN");

        getListing(newUser(), UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        mockMvc.perform(get(LfpApiPaths.LISTINGS + "/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions mine(UUID owner, String query) throws Exception {
        return mockMvc.perform(get(LfpApiPaths.MY_LISTINGS + query).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private static Map<String, Object> body(UUID gameId, String platform, boolean mic) {
        Map<String, Object> body = listingBody(gameId);
        body.put("platform", platform);
        body.put("micRequired", mic);
        return body;
    }

    private static Map<String, Object> timed(UUID gameId, Instant start) {
        Map<String, Object> body = listingBody(gameId);
        body.put("playTimeStart", start.toString());
        body.put("durationHours", 24);
        return body;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
