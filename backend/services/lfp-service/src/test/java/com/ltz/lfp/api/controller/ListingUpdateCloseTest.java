package com.ltz.lfp.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.lfp.IntegrationTestSupport;

/** LFP-12 ilan güncelleme, LFP-13 ilan kapatma ve LFP-14 durum kuralları; yalnız sahip (IDOR negatifleri). */
class ListingUpdateCloseTest extends IntegrationTestSupport {

    // ---------------------------------------------------------------- LFP-12

    @Test
    void sahipGuncellerOyunVeSureDegismezSurumArtar() throws Exception {
        UUID owner = newUser();
        UUID gameId = games.addGame("Diablo IV");
        UUID listing = openListing(owner, listingBody(gameId));
        String expiresBefore = jdbc.queryForObject("select expires_at::text from lfp_listings where id = ?",
                String.class, listing);
        long version = listingVersion(listing);
        Map<String, Object> body = updateBody(version);
        body.put("roles", List.of("Necromancer", "Barbarian"));

        updateListing(owner, listing, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("XBOX"))
                .andExpect(jsonPath("$.skillLevel").value("Gold"))
                .andExpect(jsonPath("$.roles[0]").value("Necromancer"))
                .andExpect(jsonPath("$.roles[1]").value("Barbarian"))
                .andExpect(jsonPath("$.playersNeeded").value(4))
                .andExpect(jsonPath("$.micRequired").value(false))
                .andExpect(jsonPath("$.description").value("Güncellenmiş açıklama"))
                .andExpect(jsonPath("$.gameId").value(gameId.toString()))
                .andExpect(jsonPath("$.version").value(version + 1));

        assertThat(jdbc.queryForObject("select expires_at::text from lfp_listings where id = ?", String.class,
                listing)).isEqualTo(expiresBefore);
        assertThat(jdbc.queryForList("select role from lfp_listing_roles where listing_id = ? order by position",
                String.class, listing)).containsExactly("Necromancer", "Barbarian");
    }

    @Test
    void rolSirasiDegisebilirAyniRollerYerDegistirir() throws Exception {
        UUID owner = newUser();
        Map<String, Object> create = listingBody(games.addGame("WoW"));
        create.put("roles", List.of("Tank", "Healer", "DPS"));
        UUID listing = openListing(owner, create);
        Map<String, Object> body = updateBody(listingVersion(listing));
        body.put("roles", List.of("DPS", "Tank", "Healer"));

        updateListing(owner, listing, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("DPS"))
                .andExpect(jsonPath("$.roles[2]").value("Healer"));
    }

    @Test
    void baskasininIlani403YokIlan404DegisiklikYok() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);
        long version = listingVersion(listing);

        updateListing(newUser(), listing, updateBody(version)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_LISTING_OWNER"));
        closeListing(newUser(), listing).andExpect(status().isForbidden());
        updateListing(owner, UUID.randomUUID(), updateBody(0)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        closeListing(owner, UUID.randomUUID()).andExpect(status().isNotFound());

        assertThat(listingVersion(listing)).isEqualTo(version);
        assertThat(listingStatus(listing)).isEqualTo("OPEN");
    }

    @Test
    void eskiSurumle409ConcurrentModification() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);
        long version = listingVersion(listing);
        updateListing(owner, listing, updateBody(version)).andExpect(status().isOk());

        updateListing(owner, listing, updateBody(version)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void oyunSureVeSahipAlanlariGuncellenemezBilinmeyenAlan400() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);
        for (String field : List.of("gameId", "durationHours", "ownerId", "status", "expiresAt")) {
            Map<String, Object> body = updateBody(listingVersion(listing));
            body.put(field, field.equals("durationHours") ? 100 : UUID.randomUUID().toString());
            updateListing(owner, listing, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
        Map<String, Object> noVersion = updateBody(0);
        noVersion.remove("version");
        updateListing(owner, listing, noVersion).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void guncellemeOlusturmaKurallariniUygular() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);
        Map<String, Object> body = updateBody(listingVersion(listing));
        body.put("roles", List.of());
        updateListing(owner, listing, body).andExpect(status().isBadRequest());

        Map<String, Object> pastStart = updateBody(listingVersion(listing));
        pastStart.put("playTimeStart", Instant.now().minus(Duration.ofHours(2)).toString());
        updateListing(owner, listing, pastStart).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAY_TIME"));

        Map<String, Object> afterExpiry = updateBody(listingVersion(listing));
        afterExpiry.put("playTimeStart", Instant.now().plus(Duration.ofDays(3)).toString());
        updateListing(owner, listing, afterExpiry).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAY_TIME_AFTER_EXPIRY"));
    }

    @Test
    void degismeyenGecmisBaslangicKorunabilirDigerAlanlarDuzeltilebilir() throws Exception {
        UUID owner = newUser();
        Instant start = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> create = listingBody(games.addGame("Among Us"));
        create.put("playTimeStart", start.toString());
        UUID listing = openListing(owner, create);
        // Planlanan zaman geldi/geçti: başlangıç iki saat öncesine çekilir (ilan hâlâ açık).
        Instant past = start.minus(Duration.ofHours(3));
        jdbc.update("update lfp_listings set play_time_start = ? where id = ?",
                OffsetDateTime.ofInstant(past, ZoneOffset.UTC), listing);

        Map<String, Object> body = updateBody(listingVersion(listing));
        body.put("playTimeStart", past.toString());
        body.put("description", "Oyun başladı, bir kişi daha lazım");

        updateListing(owner, listing, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.playTimeStart").value(past.toString()))
                .andExpect(jsonPath("$.description").value("Oyun başladı, bir kişi daha lazım"));
    }

    // ---------------------------------------------------------------- LFP-13 / LFP-14

    @Test
    void sahipKapatirIdempotentAktifListedenCikar() throws Exception {
        UUID owner = newUser();
        UUID gameId = games.addGame("Terraria");
        UUID listing = openListing(owner, listingBody(gameId));

        closeListing(owner, listing).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        long version = listingVersion(listing);
        closeListing(owner, listing).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(listingVersion(listing)).isEqualTo(version);
        assertThat(listingStatus(listing)).isEqualTo("CLOSED");
        search(newUser(), "?gameId=" + gameId).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void kapaliIlanGuncellenemezYenidenAcilmaz() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);
        closeListing(owner, listing).andExpect(status().isOk());

        updateListing(owner, listing, updateBody(listingVersion(listing))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_OPEN"));
        assertThat(listingStatus(listing)).isEqualTo("CLOSED");
    }

    @Test
    void suresiDolmusIlanGuncellenemezKapatilamaz() throws Exception {
        UUID owner = newUser();
        UUID listing = openListing(owner);
        makeOverdue(listing);

        updateListing(owner, listing, updateBody(listingVersion(listing))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_OPEN"));
        closeListing(owner, listing).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_OPEN"));
        assertThat(listingStatus(listing)).isEqualTo("OPEN");
    }

    @Test
    void kapatilanIlanAcikIlanSinirindanDuser() throws Exception {
        UUID owner = newUser();
        UUID first = openListing(owner);
        for (int i = 0; i < 4; i++) {
            openListing(owner);
        }
        createListing(owner, listingBody(games.addGame("Sixth"))).andExpect(status().isConflict());

        closeListing(owner, first).andExpect(status().isOk());

        createListing(owner, listingBody(games.addGame("Sixth again"))).andExpect(status().isCreated());
    }
}
