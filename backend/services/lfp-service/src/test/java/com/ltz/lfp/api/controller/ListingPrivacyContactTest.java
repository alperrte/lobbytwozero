package com.ltz.lfp.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.ltz.lfp.IntegrationTestSupport;

/**
 * LFP-17 engel/gizlilik: aralarında herhangi bir yönde engel olan kullanıcılar birbirinin ilanını görmez, detayı ve
 * iletişimi 404'tür; engel öğrenilemezse fail-closed 503. LFP-16 ilan sahibiyle iletişim: izin messaging-service'e
 * yönlendirilecek alıcıyı döner.
 */
class ListingPrivacyContactTest extends IntegrationTestSupport {

    // ---------------------------------------------------------------- LFP-17 liste ve detay

    @Test
    void engelIkiYondeIlanlariListedeGizler() throws Exception {
        UUID gameId = games.addGame("Blocked Lobby");
        UUID viewer = newUser();
        UUID blockedByViewer = newUser();
        UUID blockerOfViewer = newUser();
        UUID free = newUser();
        openListing(blockedByViewer, listingBody(gameId));
        openListing(blockerOfViewer, listingBody(gameId));
        UUID visible = openListing(free, listingBody(gameId));
        blocks.block(viewer, blockedByViewer);
        blocks.block(blockerOfViewer, viewer);

        search(viewer, "?gameId=" + gameId).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(visible.toString()));
        // Engelle ilgisi olmayan kullanıcı hepsini görür.
        search(newUser(), "?gameId=" + gameId).andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void engelKalkincaIlanYenidenGorunur() throws Exception {
        UUID gameId = games.addGame("Unblock");
        UUID viewer = newUser();
        UUID owner = newUser();
        UUID listing = openListing(owner, listingBody(gameId));
        blocks.block(owner, viewer);
        getListing(viewer, listing).andExpect(status().isNotFound());

        blocks.unblock(owner, viewer);

        getListing(viewer, listing).andExpect(status().isOk());
        search(viewer, "?gameId=" + gameId).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void engelliSahibinIlanDetayi404VarligiSizdirilmaz() throws Exception {
        UUID viewer = newUser();
        UUID owner = newUser();
        UUID listing = openListing(owner);
        blocks.block(viewer, owner);

        getListing(viewer, listing).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        getListing(newUser(), listing).andExpect(status().isOk());
    }

    @Test
    void kendiIlanlariIcinSocialaSorulmaz() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);
        blocks.setUnavailable(true);

        getListing(owner, listing).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(LfpApiPaths.MY_LISTINGS).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        assertThat(blocks.calls()).isZero();
    }

    @Test
    void socialUlasilamazsaListeDetayVeIletisimFailClosed503() throws Exception {
        UUID gameId = games.addGame("Social Down");
        UUID listing = openListing(newUser(), listingBody(gameId));
        blocks.setUnavailable(true);
        UUID viewer = newUser();

        search(viewer, "?gameId=" + gameId).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BLOCK_CHECK_UNAVAILABLE"));
        getListing(viewer, listing).andExpect(status().isServiceUnavailable());
        contact(viewer, listing).andExpect(status().isServiceUnavailable());
    }

    @Test
    void bosSonuctaSocialCagrilmaz() throws Exception {
        UUID gameId = games.addGame("Empty");
        blocks.setUnavailable(true);

        search(newUser(), "?gameId=" + gameId).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(blocks.calls()).isZero();
    }

    @Test
    void sayfaBasinaTekEngelSorgusu() throws Exception {
        UUID gameId = games.addGame("One Call");
        for (int i = 0; i < 5; i++) {
            openListing(newUser(), listingBody(gameId));
        }

        search(newUser(), "?gameId=" + gameId + "&limit=10").andExpect(jsonPath("$.items.length()").value(5));

        assertThat(blocks.calls()).isEqualTo(1);
    }

    @Test
    void engelliIlanlarAtlanincaSayfaSonrakiPartidenDoldurulurTekrarYok() throws Exception {
        UUID gameId = games.addGame("Fill Page");
        UUID viewer = newUser();
        List<String> visible = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            visible.add(0, openListing(newUser(), listingBody(gameId)).toString());
        }
        // En yeni üç ilanın sahibi engelli: ilk parti (limit 2) tamamen engelli ilanlardan oluşur.
        for (int i = 0; i < 3; i++) {
            UUID blockedOwner = newUser();
            openListing(blockedOwner, listingBody(gameId));
            blocks.block(viewer, blockedOwner);
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String query = "?gameId=" + gameId + "&limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            String body = search(viewer, query).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString();
            seen.addAll(JsonPath.<List<String>>read(body, "$.items[*].id"));
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(seen).containsExactlyElementsOf(visible);
    }

    // ---------------------------------------------------------------- LFP-16 iletişim

    @Test
    void iletisimIzniAliciOlarakSahibiDoner() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);

        contact(newUser(), listing).andExpect(status().isOk())
                .andExpect(jsonPath("$.listingId").value(listing.toString()))
                .andExpect(jsonPath("$.recipientId").value(owner.toString()));
    }

    @Test
    void kendiIlaninaIletisim400() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);

        contact(owner, listing).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_CONTACT_SELF"));
    }

    @Test
    void engelVarsaIletisim404HerIkiYonde() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);
        UUID blockedViewer = newUser();
        UUID blockingViewer = newUser();
        blocks.block(owner, blockedViewer);
        blocks.block(blockingViewer, owner);

        contact(blockedViewer, listing).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        contact(blockingViewer, listing).andExpect(status().isNotFound());
    }

    @Test
    void kapaliVeSuresiDolmusIlanaIletisim409() throws Exception {
        UUID owner = newUser();
        UUID closed = openListing(owner);
        closeListing(owner, closed).andExpect(status().isOk());
        UUID overdue = openListing(owner);
        makeOverdue(overdue);

        contact(newUser(), closed).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_OPEN"));
        contact(newUser(), overdue).andExpect(status().isConflict());
    }

    @Test
    void olmayanIlanVeKimliksizIletisim() throws Exception {
        contact(newUser(), UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(post(LfpApiPaths.LISTING_CONTACT, UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
