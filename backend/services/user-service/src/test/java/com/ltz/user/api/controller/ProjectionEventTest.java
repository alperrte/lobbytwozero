package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/**
 * Social (FriendshipChanged, UserBlocked/Unblocked) ve önerilen özet olaylarının (LibrarySummaryChanged,
 * ReviewSummaryChanged) girişi. Olay biçimi social-service'in gerçek olay kayıtlarıyla aynıdır.
 */
class ProjectionEventTest extends IntegrationTestSupport {

    // ------------------------------------------------------------ arkadaşlık

    @Test
    void arkadaslikEklenirVeKaldirilir() throws Exception {
        UUID low = newUser();
        UUID high = newUser();
        Instant now = Instant.now();

        send("social-service", friendship(low, high, "ADDED", now)).andExpect(jsonPath("$.outcome").value("PROCESSED"));
        assertThat(friendshipActive(low, high)).isTrue();

        send("social-service", friendship(low, high, "REMOVED", now.plusSeconds(1))).andExpect(status().isOk());
        assertThat(friendshipActive(low, high)).isFalse();
    }

    @Test
    void siraBozukEskiEklemeKaldirmayiEzmez() throws Exception {
        UUID low = newUser();
        UUID high = newUser();
        Instant now = Instant.now();

        send("social-service", friendship(low, high, "REMOVED", now)).andExpect(status().isOk());
        send("social-service", friendship(low, high, "ADDED", now.minusSeconds(30))).andExpect(status().isOk());

        assertThat(friendshipActive(low, high)).isFalse();
    }

    @Test
    void gecersizArkadaslikYuku400() throws Exception {
        UUID user = newUser();

        send("social-service", friendship(user, user, "ADDED", Instant.now())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT"));
        send("social-service", friendship(user, newUser(), "MAYBE", Instant.now())).andExpect(status().isBadRequest());
        send("social-service", envelope("FriendshipChanged", "social-service", UUID.randomUUID(),
                "{\"userLowId\":\"x\",\"userHighId\":\"%s\",\"change\":\"ADDED\"}".formatted(user), Instant.now()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ engel

    @Test
    void engelKonurVeKaldirilirSiraBozuklugunaDayanikli() throws Exception {
        UUID blocker = newUser();
        UUID blocked = newUser();
        Instant now = Instant.now();

        send("social-service", block("UserBlocked", blocker, blocked, now)).andExpect(status().isOk());
        assertThat(blockActive(blocker, blocked)).isTrue();
        send("social-service", block("UserUnblocked", blocker, blocked, now.plusSeconds(5))).andExpect(status().isOk());
        assertThat(blockActive(blocker, blocked)).isFalse();
        // Gecikmiş eski "engellendi" olayı kaldırmayı geri almaz.
        send("social-service", block("UserBlocked", blocker, blocked, now.plusSeconds(1))).andExpect(status().isOk());
        assertThat(blockActive(blocker, blocked)).isFalse();
    }

    @Test
    void kendiniEngelleme400() throws Exception {
        UUID user = newUser();

        send("social-service", block("UserBlocked", user, user, Instant.now())).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ üretici kuralları

    @Test
    void socialOlayiniBaskaServisYayinlayamaz() throws Exception {
        UUID low = newUser();
        UUID high = newUser();

        // Token başka servis, zarf social-service: çağıran ≠ üretici.
        send("community-service", friendship(low, high, "ADDED", Instant.now())).andExpect(status().isForbidden());
        // Token ve zarf aynı ama olay türü bu üreticiye ait değil.
        send("community-service", envelope("FriendshipChanged", "community-service", UUID.randomUUID(),
                "{\"userLowId\":\"%s\",\"userHighId\":\"%s\",\"change\":\"ADDED\"}".formatted(low, high), Instant.now()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EVENT_PRODUCER_MISMATCH"));
        assertThat(jdbc.queryForObject("select count(*) from social_friendships where user_low_id = ?", Integer.class, low))
                .isZero();
    }

    // ------------------------------------------------------------ özet sayaçları (önerilen sözleşme)

    @Test
    void kutuphaneVeIncelemeOzetleriMutlakSayiOlarakYazilir() throws Exception {
        UUID user = newUser();
        Instant now = Instant.now();

        send("library-service", envelope("LibrarySummaryChanged", "library-service", user,
                "{\"userId\":\"%s\",\"collectionCount\":5,\"completedCount\":2,\"favoriteCount\":1}".formatted(user), now))
                .andExpect(status().isOk());
        send("review-service", envelope("ReviewSummaryChanged", "review-service", user,
                "{\"userId\":\"%s\",\"reviewCount\":3}".formatted(user), now)).andExpect(status().isOk());
        // Eski olay yeni sayıyı ezmez.
        send("library-service", envelope("LibrarySummaryChanged", "library-service", user,
                "{\"userId\":\"%s\",\"collectionCount\":1,\"completedCount\":0,\"favoriteCount\":0}".formatted(user),
                now.minusSeconds(60))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select library_collection_count from profile_counters where user_id = ?",
                Integer.class, user)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select review_count from profile_counters where user_id = ?", Integer.class, user))
                .isEqualTo(3);
    }

    @Test
    void gecersizOzetYuku400() throws Exception {
        UUID user = newUser();

        send("library-service", envelope("LibrarySummaryChanged", "library-service", user,
                "{\"userId\":\"%s\",\"collectionCount\":-1,\"completedCount\":0,\"favoriteCount\":0}".formatted(user),
                Instant.now())).andExpect(status().isBadRequest());
        send("review-service", envelope("ReviewSummaryChanged", "review-service", user,
                "{\"userId\":\"%s\",\"reviewCount\":\"3\"}".formatted(user), Instant.now()))
                .andExpect(status().isBadRequest());
        send("review-service", envelope("ReviewSummaryChanged", "review-service", user,
                "{\"userId\":\"%s\",\"reviewCount\":1.5}".formatted(user), Instant.now()))
                .andExpect(status().isBadRequest());
        // userId zarftaki aggregateId ile aynı olmalı.
        send("review-service", envelope("ReviewSummaryChanged", "review-service", user,
                "{\"userId\":\"%s\",\"reviewCount\":3}".formatted(newUser()), Instant.now()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ yardımcılar

    /** {@code caller} servisinin Auth'tan aldığı olay token'ıyla gönderir; zarftaki üretici gövdededir. */
    private ResultActions send(String caller, String body) throws Exception {
        String token = sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, serviceClaims("svc:" + caller, EVENTS_SCOPE).build());
        return mockMvc.perform(post(InternalApiPaths.EVENTS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private boolean friendshipActive(UUID low, UUID high) {
        return jdbc.queryForObject("select active from social_friendships where user_low_id = ? and user_high_id = ?",
                Boolean.class, low, high);
    }

    private boolean blockActive(UUID blocker, UUID blocked) {
        return jdbc.queryForObject("select active from social_blocks where blocker_id = ? and blocked_id = ?",
                Boolean.class, blocker, blocked);
    }

    private static String friendship(UUID low, UUID high, String change, Instant at) {
        return envelope("FriendshipChanged", "social-service", UUID.randomUUID(),
                "{\"userLowId\":\"%s\",\"userHighId\":\"%s\",\"change\":\"%s\"}".formatted(low, high, change), at);
    }

    private static String block(String type, UUID blocker, UUID blocked, Instant at) {
        return envelope(type, "social-service", UUID.randomUUID(),
                "{\"blockerId\":\"%s\",\"blockedId\":\"%s\"}".formatted(blocker, blocked), at);
    }

    private static String envelope(String type, String producer, UUID aggregate, String payload, Instant occurredAt) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"%s","producer":"%s",\
                "aggregateId":"%s","payload":%s}""".formatted(UUID.randomUUID(), type, occurredAt, producer, aggregate,
                payload);
    }
}
