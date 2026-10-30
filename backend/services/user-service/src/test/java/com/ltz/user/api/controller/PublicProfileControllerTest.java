package com.ltz.user.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/**
 * Açık profil (USER-05, USER-06, USER-12): misafir/üye/arkadaş/sahip görünümü, engel ve hesap durumu gizlemesi, özet
 * durumları. Social yansıması bu bağlamda güvenilir değildir (varsayılan): arkadaş sayısı {@code UNAVAILABLE}.
 */
class PublicProfileControllerTest extends IntegrationTestSupport {

    // ------------------------------------------------------------ misafir (K5)

    @Test
    void misafirVarsayilandaYalnizTemelKartiGorur() throws Exception {
        UUID owner = profile("ACTIVE");

        view(owner, null).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(jsonPath("$.userId").value(owner.toString()))
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.displayName").value("Gorunen Ad"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.visible.library").value(false))
                .andExpect(jsonPath("$.visible.friendsList").value(false))
                .andExpect(jsonPath("$.summary.library.status").value("HIDDEN"))
                .andExpect(jsonPath("$.summary.reviews.status").value("HIDDEN"))
                .andExpect(jsonPath("$.summary.friends.status").value("HIDDEN"));
    }

    @Test
    void misafirAyrintiliIzindeAyrintiVePublicBolumleriGorur() throws Exception {
        UUID owner = profile("ACTIVE");
        insertPrivacy(owner, "FRIENDS", "PUBLIC", "PRIVATE", "PRIVATE", "DETAILED");

        view(owner, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.bio").value("biyografi"))
                .andExpect(jsonPath("$.details.country").value("TR"))
                .andExpect(jsonPath("$.visible.library").value(true))
                .andExpect(jsonPath("$.visible.friendsList").value(false))
                .andExpect(jsonPath("$.visible.hardware").value(false))
                .andExpect(jsonPath("$.summary.library.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.summary.library.collection").doesNotExist());
    }

    // ------------------------------------------------------------ üye, arkadaş, sahip

    @Test
    void uyeAyrintiVePublicBolumleriGorurFriendsBolumleriniGormez() throws Exception {
        UUID owner = profile("ACTIVE");

        view(owner, newUser()).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.bio").value("biyografi"))
                .andExpect(jsonPath("$.details.playerTypes[0]").value("CASUAL"))
                .andExpect(jsonPath("$.visible.library").value(true))
                .andExpect(jsonPath("$.visible.friendsList").value(false))
                .andExpect(jsonPath("$.visible.hardware").value(false))
                .andExpect(jsonPath("$.visible.performance").value(false))
                .andExpect(jsonPath("$.summary.friends.status").value("HIDDEN"))
                .andExpect(jsonPath("$.summary.reviews.status").value("UNAVAILABLE"));
    }

    @Test
    void arkadasFriendsBolumleriniGorurPrivateGormez() throws Exception {
        UUID owner = profile("ACTIVE");
        UUID friend = newUser();
        befriend(owner, friend);

        view(owner, friend).andExpect(status().isOk())
                .andExpect(jsonPath("$.visible.friendsList").value(true))
                .andExpect(jsonPath("$.visible.hardware").value(true))
                .andExpect(jsonPath("$.visible.performance").value(false))
                // Yansıma güvenilir değilken arkadaş sayısı uydurulmaz.
                .andExpect(jsonPath("$.summary.friends.status").value("UNAVAILABLE"));
    }

    @Test
    void sahipHerSeyiGorur() throws Exception {
        UUID owner = profile("ACTIVE");
        insertPrivacy(owner, "PRIVATE", "PRIVATE", "PRIVATE", "PRIVATE", "BASIC");

        view(owner, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.bio").value("biyografi"))
                .andExpect(jsonPath("$.visible.friendsList").value(true))
                .andExpect(jsonPath("$.visible.library").value(true))
                .andExpect(jsonPath("$.visible.hardware").value(true))
                .andExpect(jsonPath("$.visible.performance").value(true));
    }

    @Test
    void privateKutuphaneUyeyeVeArkadasaGizliProfilYineGorunur() throws Exception {
        UUID owner = profile("ACTIVE");
        UUID friend = newUser();
        befriend(friend, owner);
        insertPrivacy(owner, "PRIVATE", "PRIVATE", "PRIVATE", "PRIVATE", "DETAILED");
        insertCounters(owner, 12, 3, 4, 7);

        view(owner, friend).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Gorunen Ad"))
                .andExpect(jsonPath("$.summary.library.status").value("HIDDEN"))
                .andExpect(jsonPath("$.summary.library.collection").doesNotExist())
                .andExpect(jsonPath("$.summary.reviews.count").value(7));
        view(owner, null).andExpect(jsonPath("$.summary.library.status").value("HIDDEN"));
    }

    @Test
    void ozetSayilariKaynaktanGelirseGosterilir() throws Exception {
        UUID owner = profile("ACTIVE");
        insertCounters(owner, 12, 3, 4, 7);

        view(owner, newUser()).andExpect(jsonPath("$.summary.library.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.summary.library.collection").value(12))
                .andExpect(jsonPath("$.summary.library.completed").value(3))
                .andExpect(jsonPath("$.summary.library.favorite").value(4))
                .andExpect(jsonPath("$.summary.reviews.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.summary.reviews.count").value(7));
    }

    // ------------------------------------------------------------ gizleme: engel, durum, yokluk

    @Test
    void engelHerIkiYondeProfiliGizler404AyniYanit() throws Exception {
        UUID owner = profile("ACTIVE");
        UUID blockedByOwner = newUser();
        UUID blocker = newUser();
        block(owner, blockedByOwner);
        block(blocker, owner);

        view(owner, blockedByOwner).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
        view(owner, blocker).andExpect(status().isNotFound());
        view(owner, newUser()).andExpect(status().isOk());
    }

    @Test
    void yasakliDevreDisiVeSilinmekteOlanHesap404AskidakiGorunur() throws Exception {
        for (String hidden : new String[] {"BANNED", "DISABLED", "DELETION_REQUESTED", "PENDING_VERIFICATION"}) {
            UUID owner = profile(hidden);
            view(owner, newUser()).andExpect(status().isNotFound());
            view(owner, null).andExpect(status().isNotFound());
        }
        view(profile("SUSPENDED"), newUser()).andExpect(status().isOk());
        view(UUID.randomUUID(), newUser()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
    }

    @Test
    void kaldirilmisEngelArtikGizlemez() throws Exception {
        UUID owner = profile("ACTIVE");
        UUID viewer = newUser();
        jdbc.update("insert into social_blocks (blocker_id, blocked_id, active, changed_at) values (?, ?, false, now())",
                owner, viewer);

        view(owner, viewer).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ kullanıcı adıyla

    @Test
    void kullaniciAdiylaBuyukKucukHarfeDuyarsiz() throws Exception {
        UUID owner = newUser();
        String username = "Oyuncu_" + suffix();
        insertNamedProfile(owner, username, "Ad Soyad", "ACTIVE");

        // Kullanıcı adları ASCII'dir; büyük harfe yerelden bağımsız çevrilir (Türkçe yerelde 'i' → 'İ' olurdu).
        byUsername(username.toUpperCase(java.util.Locale.ROOT), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(owner.toString()))
                .andExpect(jsonPath("$.username").value(username));
        byUsername("yok_" + suffix(), null).andExpect(status().isNotFound());
        byUsername("a b", null).andExpect(status().isNotFound());
        byUsername("x".repeat(21), null).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ token kuralları

    @Test
    void gecersizTokenMisafirSayilmaz401ServisTokeni401() throws Exception {
        UUID owner = profile("ACTIVE");
        String expired = accessToken(newUser(), claims -> claims
                .issueTime(Date.from(Instant.now().minusSeconds(900)))
                .notBeforeTime(Date.from(Instant.now().minusSeconds(900)))
                .expirationTime(Date.from(Instant.now().minusSeconds(600))));

        mockMvc.perform(get(UserApiPaths.PROFILE, owner).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(UserApiPaths.PROFILE, owner).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + serviceToken(owner, UnaryOperator.identity()))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(UserApiPaths.PROFILE, "not-a-uuid")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ yardımcılar

    private UUID profile(String status) {
        UUID owner = newUser();
        insertNamedProfile(owner, "u" + suffix(), "Gorunen Ad", status);
        return owner;
    }

    private void insertCounters(UUID owner, int collection, int completed, int favorite, int reviews) {
        jdbc.update("""
                insert into profile_counters (user_id, library_collection_count, library_completed_count,
                                              library_favorite_count, library_changed_at, review_count, review_changed_at)
                values (?, ?, ?, ?, now(), ?, now())""", owner, collection, completed, favorite, reviews);
    }

    private ResultActions view(UUID owner, UUID viewer) throws Exception {
        var request = get(UserApiPaths.PROFILE, owner);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(viewer));
        }
        return mockMvc.perform(request);
    }

    private ResultActions byUsername(String username, UUID viewer) throws Exception {
        var request = get(UserApiPaths.PROFILE_BY_USERNAME, username);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(viewer));
        }
        return mockMvc.perform(request);
    }

    /** Testler aynı veritabanını paylaşır: kullanıcı adı çakışmasın diye 10 karakterlik rastgele [a-z0-9]. */
    static String suffix() {
        long atLeastTenDigits = 101_559_956_668_416L; // 36^9
        return Long.toString(ThreadLocalRandom.current().nextLong(atLeastTenDigits, Long.MAX_VALUE), 36)
                .substring(0, 10);
    }
}
