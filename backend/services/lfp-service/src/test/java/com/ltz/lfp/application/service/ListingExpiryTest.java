package com.ltz.lfp.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.lfp.IntegrationTestSupport;

/** LFP-15: süresi dolan ilan otomatik {@code EXPIRED} olur ve aktif listelerden çıkar. */
class ListingExpiryTest extends IntegrationTestSupport {

    @Autowired
    private ListingExpiryService expiry;

    @Test
    void suresiDolanAcikIlanExpiredOlurDigerleriDegismezSurumArtar() throws Exception {
        UUID owner = newUser();
        UUID due = openListing(owner);
        UUID future = openListing(owner);
        UUID closed = openListing(owner);
        closeListing(owner, closed).andExpect(status().isOk());
        makeOverdue(due);
        long version = listingVersion(due);

        assertThat(expiry.expireDue()).isPositive();

        assertThat(listingStatus(due)).isEqualTo("EXPIRED");
        assertThat(listingVersion(due)).isEqualTo(version + 1);
        assertThat(listingStatus(future)).isEqualTo("OPEN");
        assertThat(listingStatus(closed)).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("select closed_at from lfp_listings where id = ?", Object.class, due))
                .isNull();
    }

    @Test
    void ikinciCalistirmaIslemsiz() throws Exception {
        UUID due = openListing(newUser());
        makeOverdue(due);
        expiry.expireDue();
        long version = listingVersion(due);

        expiry.expireDue();

        assertThat(listingVersion(due)).isEqualTo(version);
        assertThat(listingStatus(due)).isEqualTo("EXPIRED");
    }

    @Test
    void zamanlayiciCalismadanOnceDeSuresiGecenIlanAktifListedeYok() throws Exception {
        UUID gameId = games.addGame("Expired Before Job");
        UUID due = openListing(newUser(), listingBody(gameId));
        makeOverdue(due);

        search(newUser(), "?gameId=" + gameId).andExpect(jsonPath("$.items.length()").value(0));
        getListing(newUser(), due).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void suresiDolanIlanSahibininAcikIlanSinirindanDuser() throws Exception {
        UUID owner = newUser();
        UUID first = openListing(owner);
        for (int i = 0; i < 4; i++) {
            openListing(owner);
        }
        makeOverdue(first);
        expiry.expireDue();

        createListing(owner, listingBody(games.addGame("After expiry"))).andExpect(status().isCreated());
    }
}
