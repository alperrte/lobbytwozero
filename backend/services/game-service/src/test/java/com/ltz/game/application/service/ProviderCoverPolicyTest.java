package com.ltz.game.application.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCoverPolicyTest {
    static final String HASH = "ce4d5e53b36cb9d3c4309d1df72bf8663bbbc7ef";

    @Test void steamHeaderIsNormalizedToTheFastlyHostWithoutQueryAndKeepsTheContentHash() {
        String expected = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/3595230/" + HASH + "/header.jpg";
        assertEquals(expected, ProviderCoverPolicy.steamHeader("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/3595230/" + HASH + "/header.jpg?t=1"));
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/570/header.jpg",
                ProviderCoverPolicy.steamHeader("https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/570/header.jpg?t=2"));
        assertNull(ProviderCoverPolicy.steamHeader("https://evil.example/store_item_assets/steam/apps/570/header.jpg"));
        assertNull(ProviderCoverPolicy.steamHeader("http://shared.fastly.steamstatic.com/store_item_assets/steam/apps/570/header.jpg"));
        assertNull(ProviderCoverPolicy.steamHeader("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/570/../x/header.jpg"));
        assertNull(ProviderCoverPolicy.steamHeader(null));
    }

    @Test void alternativeHeaderAssetNamesAreAccepted() {
        String hash = "48a93d6445a67520ea49833c93dae8f2e68fc540";
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1091500/" + hash + "/header_alt_assets_9.jpg",
                ProviderCoverPolicy.steamHeader("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1091500/" + hash + "/header_alt_assets_9.jpg?t=3"));
        assertTrue(ProviderCoverPolicy.accepts("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1091500/" + hash + "/header_alt_assets_9.jpg"));
        assertNull(ProviderCoverPolicy.steamHeader("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1091500/" + hash + "/header_evil.jpg"));
    }

    @Test void igdbCoverIsBuiltOnlyFromASafeImageId() {
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co4sbw.jpg", ProviderCoverPolicy.igdbCover("co4sbw"));
        assertNull(ProviderCoverPolicy.igdbCover("../x"));
        assertNull(ProviderCoverPolicy.igdbCover("ab"));
        assertNull(ProviderCoverPolicy.igdbCover(null));
    }

    @Test void onlyNormalizedProviderAddressesAreAcceptedForStorage() {
        assertTrue(ProviderCoverPolicy.accepts("https://images.igdb.com/igdb/image/upload/t_cover_big/co4sbw.jpg"));
        assertTrue(ProviderCoverPolicy.accepts("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/3595230/" + HASH + "/header.jpg"));
        assertFalse(ProviderCoverPolicy.accepts("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/3595230/" + HASH + "/header.jpg?t=1"), "sorgu saklanmaz");
        assertFalse(ProviderCoverPolicy.accepts("https://example.com/a.jpg"));
        assertFalse(ProviderCoverPolicy.accepts("javascript:alert(1)"));
        assertFalse(ProviderCoverPolicy.accepts(null));
    }

    @Test void steamOutranksIgdbAndAdminOrUnknownSourcesAreNeverReplaced() {
        assertTrue(ProviderCoverPolicy.rank("STEAM") > ProviderCoverPolicy.rank("IGDB"));
        assertTrue(ProviderCoverPolicy.rank("ADMIN") > ProviderCoverPolicy.rank("STEAM"));
        assertTrue(ProviderCoverPolicy.rank(null) > ProviderCoverPolicy.rank("STEAM"));
    }
}
