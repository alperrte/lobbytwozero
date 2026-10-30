package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.ltz.user.IntegrationTestSupport;

/** Steam bağlantısı (USER-07/08): Auth olaylarının yansıması, izin yönetimi ve açık profilde gösterim. */
class LinkedAccountTest extends IntegrationTestSupport {

    private final String steamA = randomSteamId();
    private final String steamB = randomSteamId();
    private static final String PROFILE_URL = "https://steamcommunity.com/profiles/";
    private static final String SELECT_ACTIVE = "select active from linked_accounts where user_id = ?";

    // ------------------------------------------------------------ olay girişi

    @Test
    void baglanmaSatiriEtkinVeIzinsizYazar() throws Exception {
        UUID user = newUser();

        linked(user, steamA, Instant.now()).andExpect(jsonPath("$.outcome").value("PROCESSED"));

        assertThat(jdbc.queryForObject("select active and not show_on_profile and external_id = ? "
                + "from linked_accounts where user_id = ?", Boolean.class, steamA, user)).isTrue();
        assertThat(profileCount(user)).isEqualTo(1);
    }

    @Test
    void ayniKimlikIzniKorurFarkliKimlikSifirlar() throws Exception {
        UUID user = newUser();
        Instant now = Instant.now();
        linked(user, steamA, now);
        patch(user, 0, true).andExpect(status().isOk());

        linked(user, steamA, now.plusSeconds(1));
        assertThat(showOnProfile(user)).isTrue();

        linked(user, steamB, now.plusSeconds(2));
        assertThat(showOnProfile(user)).isFalse();
        assertThat(jdbc.queryForObject("select consent_at is null from linked_accounts where user_id = ?",
                Boolean.class, user)).isTrue();
    }

    @Test
    void kaldirmaKimligiSilerSatirKalirVeEskiBaglamaGeriAlmaz() throws Exception {
        UUID user = newUser();
        Instant now = Instant.now();
        linked(user, steamA, now);

        unlinked(user, now.plusSeconds(5)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select not active and external_id is null from linked_accounts "
                + "where user_id = ?", Boolean.class, user)).isTrue();

        linked(user, steamA, now.plusSeconds(1)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(SELECT_ACTIVE, Boolean.class, user)).isFalse();
    }

    @Test
    void baglantiOlmadanGelenKaldirmaSonrakiEskiBaglamayiEngeller() throws Exception {
        UUID user = newUser();
        Instant now = Instant.now();

        unlinked(user, now).andExpect(status().isOk());
        linked(user, steamA, now.minusSeconds(30)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject(SELECT_ACTIVE, Boolean.class, user)).isFalse();
    }

    @Test
    void gecersizOlaylar400() throws Exception {
        UUID user = newUser();
        UUID other = newUser();
        linked(other, steamA, Instant.now());

        linked(user, "7656119800000000", Instant.now()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT"));
        linked(user, "7656119800000000x", Instant.now()).andExpect(status().isBadRequest());
        linked(user, steamA, Instant.now()).andExpect(status().isBadRequest());
        send("auth-service", event("ExternalIdentityLinked", user,
                "{\"accountId\":\"%s\",\"provider\":\"STEAM\",\"providerSubject\":\"%s\"}".formatted(other, steamB),
                Instant.now())).andExpect(status().isBadRequest());
        send("auth-service", event("ExternalIdentityLinked", user,
                "{\"accountId\":\"%s\",\"provider\":\"STEAM\"}".formatted(user), Instant.now()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from linked_accounts where user_id = ?", Integer.class, user))
                .isZero();
    }

    @Test
    void bilinmeyenSaglayiciYokSayilirYanlisUreticiVeTekrarEtkisiz() throws Exception {
        UUID user = newUser();
        Instant now = Instant.now();

        send("auth-service", event("ExternalIdentityLinked", user,
                "{\"accountId\":\"%s\",\"provider\":\"DISCORD\",\"providerSubject\":\"123\"}".formatted(user), now))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from linked_accounts where user_id = ?", Integer.class, user))
                .isZero();

        String body = event("ExternalIdentityLinked", user,
                "{\"accountId\":\"%s\",\"provider\":\"steam\",\"providerSubject\":\"%s\"}".formatted(user, steamA), now);
        send("auth-service", body).andExpect(jsonPath("$.outcome").value("PROCESSED"));
        send("auth-service", body).andExpect(jsonPath("$.outcome").value("DUPLICATE"));

        // Auth olayını başka bir servis token'ıyla yayınlamak reddedilir.
        send("social-service", event("ExternalIdentityUnlinked", user,
                "{\"accountId\":\"%s\",\"provider\":\"STEAM\"}".formatted(user), now.plusSeconds(9)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ kullanıcı uçları

    @Test
    void listeBagliOlmayanVeBagliDurumuGosterir() throws Exception {
        UUID user = newUser();

        get(user).andExpect(status().isOk()).andExpect(jsonPath("$.accounts[0].provider").value("STEAM"))
                .andExpect(jsonPath("$.accounts[0].linked").value(false))
                .andExpect(jsonPath("$.accounts[0].externalId").doesNotExist());

        linked(user, steamA, Instant.now());
        get(user).andExpect(jsonPath("$.accounts[0].linked").value(true))
                .andExpect(jsonPath("$.accounts[0].externalId").value(steamA))
                .andExpect(jsonPath("$.accounts[0].profileUrl").value(PROFILE_URL + steamA))
                .andExpect(jsonPath("$.accounts[0].showOnProfile").value(false));
    }

    @Test
    void izinAcKapatIdempotentVeSurumKurali() throws Exception {
        UUID user = newUser();
        linked(user, steamA, Instant.now());

        patch(user, 0, true).andExpect(status().isOk()).andExpect(jsonPath("$.showOnProfile").value(true))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select consent_at is not null from linked_accounts where user_id = ?",
                Boolean.class, user)).isTrue();
        patch(user, 1, true).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        patch(user, 0, false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        patch(user, 1, false).andExpect(status().isOk()).andExpect(jsonPath("$.showOnProfile").value(false));
    }

    @Test
    void patchHatalari() throws Exception {
        UUID user = newUser();

        patch(user, 0, true).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINKED_ACCOUNT_NOT_FOUND"));
        linked(user, steamA, Instant.now());
        mockMvc.perform(MockMvcRequestBuilders.patch(UserApiPaths.ME_LINKED_ACCOUNTS + "/discord")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"showOnProfile\":true}")).andExpect(status().isNotFound());
        patchRaw(user, "{\"showOnProfile\":true}").andExpect(status().isBadRequest());
        patchRaw(user, "{\"version\":-1,\"showOnProfile\":true}").andExpect(status().isBadRequest());
        patchRaw(user, "{\"version\":0,\"showOnProfile\":true,\"externalId\":\"1\"}").andExpect(status().isBadRequest());
        unlinked(user, Instant.now().plusSeconds(5));
        patch(user, 3, true).andExpect(status().isNotFound());
    }

    @Test
    void tokenKurallari() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(UserApiPaths.ME_LINKED_ACCOUNTS))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get(UserApiPaths.ME_LINKED_ACCOUNTS).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + eventToken(newUser(), claims -> claims))).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ açık profil

    @Test
    void aciProfildeYalnizIzinliVeEtkinBaglantiGorunur() throws Exception {
        UUID owner = newUser();
        UUID viewer = newUser();
        insertNamedProfile(owner, "steamci", "Steamci", "ACTIVE");
        linked(owner, steamA, Instant.now());

        profile(owner, viewer).andExpect(jsonPath("$.details.steam").doesNotExist());

        patch(owner, 0, true).andExpect(status().isOk());
        profile(owner, viewer).andExpect(jsonPath("$.details.steam.steamId").value(steamA))
                .andExpect(jsonPath("$.details.steam.profileUrl").value(PROFILE_URL + steamA));
        // Misafir ayrıntı göremez: Steam de görünmez.
        mockMvc.perform(MockMvcRequestBuilders.get(UserApiPaths.PROFILES + "/" + owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.details").doesNotExist());

        unlinked(owner, Instant.now().plusSeconds(5));
        profile(owner, viewer).andExpect(jsonPath("$.details.steam").doesNotExist());
    }

    // ------------------------------------------------------------ yardımcılar

    private static String randomSteamId() {
        return "7656119" + String.format("%010d", java.util.concurrent.ThreadLocalRandom.current().nextLong(10_000_000_000L));
    }

    private boolean showOnProfile(UUID user) {
        return jdbc.queryForObject("select show_on_profile from linked_accounts where user_id = ?", Boolean.class, user);
    }

    private ResultActions profile(UUID owner, UUID viewer) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(UserApiPaths.PROFILES + "/" + owner)
                .header(HttpHeaders.AUTHORIZATION, bearer(viewer))).andExpect(status().isOk());
    }

    private ResultActions get(UUID user) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(UserApiPaths.ME_LINKED_ACCOUNTS)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions patch(UUID user, long version, boolean show) throws Exception {
        return patchRaw(user, "{\"version\":%d,\"showOnProfile\":%s}".formatted(version, show));
    }

    private ResultActions patchRaw(UUID user, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.patch(UserApiPaths.ME_LINKED_ACCOUNTS + "/steam")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions linked(UUID user, String steamId, Instant at) throws Exception {
        return send("auth-service", event("ExternalIdentityLinked", user,
                "{\"accountId\":\"%s\",\"provider\":\"STEAM\",\"providerSubject\":\"%s\"}".formatted(user, steamId), at));
    }

    private ResultActions unlinked(UUID user, Instant at) throws Exception {
        return send("auth-service", event("ExternalIdentityUnlinked", user,
                "{\"accountId\":\"%s\",\"provider\":\"STEAM\"}".formatted(user), at));
    }

    private ResultActions send(String caller, String body) throws Exception {
        String token = "auth-service".equals(caller) ? eventToken(aggregateOf(body), claims -> claims)
                : sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, serviceClaims("svc:" + caller, EVENTS_SCOPE).build());
        return mockMvc.perform(MockMvcRequestBuilders.post(InternalApiPaths.EVENTS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Auth'un token'ındaki {@code account_id}, olayın {@code aggregateId} değeridir. */
    private static UUID aggregateOf(String body) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"aggregateId\":\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return UUID.fromString(matcher.group(1));
    }

    private static String event(String type, UUID aggregate, String payload, Instant at) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"%s","producer":"auth-service",\
                "aggregateId":"%s","payload":%s}""".formatted(UUID.randomUUID(), type, at, aggregate, payload);
    }
}
