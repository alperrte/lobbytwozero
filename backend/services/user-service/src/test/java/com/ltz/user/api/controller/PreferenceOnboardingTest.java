package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;
import com.ltz.user.IntegrationTestSupport;

/** Tercihler (USER-10) ve ilk kurulum (USER-09): varsayılanlar, sürüm kuralı, doğrulama, idempotentlik. */
class PreferenceOnboardingTest extends IntegrationTestSupport {

    private static final String PREFS = UserApiPaths.ME_PREFERENCES;
    private static final String ONBOARDING = UserApiPaths.ME_ONBOARDING;
    private static final String COMPLETE = UserApiPaths.ME_ONBOARDING_COMPLETE;

    // ------------------------------------------------------------ tercihler

    @Test
    void okumaVarsayilanDonerVeSatirOlusturmaz() throws Exception {
        UUID user = newUser();

        mockMvc.perform(MockMvcRequestBuilders.get(PREFS).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locale").value("tr"))
                .andExpect(jsonPath("$.theme").value("SYSTEM")).andExpect(jsonPath("$.reducedMotion").value(false))
                .andExpect(jsonPath("$.version").value(0));

        assertThat(rows("user_preferences", user)).isZero();
    }

    @Test
    void ilkPutProfiliAcarSurumArtarVeDegisiklikYoksaArtmaz() throws Exception {
        UUID user = newUser();

        put(user, prefs(0, "en", "DARK")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.locale").value("en"));
        assertThat(profileCount(user)).isEqualTo(1);

        put(user, prefs(0, "en", "DARK")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        put(user, prefs(0, "tr", "LIGHT")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        put(user, prefs(0, "tr", "DARK")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void gecersizTercihler400() throws Exception {
        UUID user = newUser();

        put(user, prefs(0, "de", "DARK")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCALE"));
        put(user, prefs(0, "tr", "NEON")).andExpect(status().isBadRequest());
        put(user, "{\"version\":0,\"locale\":\"tr\",\"theme\":\"DARK\",\"reducedMotion\":true}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        put(user, prefs(-1, "tr", "DARK")).andExpect(status().isBadRequest());
        put(user, prefs(0, "tr", "DARK").replace("}", ",\"x\":1}")).andExpect(status().isBadRequest());
    }

    @Test
    void esZamanliIlkKayitlardanYalnizBiriBasarili() throws Exception {
        UUID user = newUser();

        List<Integer> codes = parallel(() -> put(user, prefs(0, "en", "DARK")).andReturn().getResponse().getStatus(),
                () -> put(user, prefs(0, "tr", "LIGHT")).andReturn().getResponse().getStatus());

        // Sıralı gelirse ikisi de sürüm 0'ı güncel görür (200, 200); gerçekten yarışırsa biri 409 alır. Tek satır kalır.
        assertThat(codes).contains(200).allMatch(code -> code == 200 || code == 409);
        assertThat(rows("user_preferences", user)).isEqualTo(1);
    }

    @Test
    void tokenKurallari() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(PREFS)).andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get(ONBOARDING).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + eventToken(newUser(), claims -> claims))).andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.post(COMPLETE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ onboarding

    @Test
    void durumEksikAdimlariGosterir() throws Exception {
        UUID user = newUser();

        mockMvc.perform(MockMvcRequestBuilders.get(ONBOARDING).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.missing.length()").value(2));
    }

    @Test
    void tamamlamaProfilVeTercihYazarIdempotentKalir() throws Exception {
        UUID user = newUser();
        long version = onboardingVersion(user);

        complete(user, onboarding(version, "[\"rpg\"]", "[\"pc\"]", ",\"locale\":\"EN\",\"theme\":\"DARK\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.missing.length()").value(0));
        assertThat(jdbc.queryForObject("select favorite_genres[1] from user_profiles where user_id = ?", String.class,
                user)).isEqualTo("rpg");
        assertThat(jdbc.queryForObject("select locale from user_preferences where user_id = ?", String.class, user))
                .isEqualTo("en");
        Long after = jdbc.queryForObject("select version from user_profiles where user_id = ?", Long.class, user);

        // İkinci çağrı (eski sürümle de) hiçbir şeyi değiştirmez.
        complete(user, onboarding(version, "[\"fps\"]", "[\"ps\"]", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
        assertThat(jdbc.queryForObject("select favorite_genres[1] from user_profiles where user_id = ?", String.class,
                user)).isEqualTo("rpg");
        assertThat(jdbc.queryForObject("select version from user_profiles where user_id = ?", Long.class, user))
                .isEqualTo(after);
    }

    @Test
    void dilTemaVerilmezseTercihSatiriOlusmaz() throws Exception {
        UUID user = newUser();

        complete(user, onboarding(onboardingVersion(user), "[\"rpg\"]", "[\"pc\"]", "")).andExpect(status().isOk());

        assertThat(rows("user_preferences", user)).isZero();
    }

    @Test
    void yalnizDilVerilinceTemaVarsayilanKalir() throws Exception {
        UUID user = newUser();

        complete(user, onboarding(onboardingVersion(user), "[\"rpg\"]", "[\"pc\"]", ",\"locale\":\"en\""))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select theme from user_preferences where user_id = ?", String.class, user))
                .isEqualTo("SYSTEM");
    }

    @Test
    void eksikGecersizVeEskiSurum() throws Exception {
        UUID user = newUser();
        long version = onboardingVersion(user);

        complete(user, onboarding(version, "[]", "[\"pc\"]", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONBOARDING_INCOMPLETE"));
        complete(user, onboarding(version, "[\"rpg\"]", "[]", "")).andExpect(status().isBadRequest());
        complete(user, onboarding(version, "[\"BÖYLE KOD\"]", "[\"pc\"]", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FAVORITE_GENRES"));
        complete(user, onboarding(version, "[\"rpg\"]", "[\"pc\"]", ",\"locale\":\"de\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LOCALE"));
        complete(user, onboarding(version, "[\"rpg\"]", "[\"pc\"]", ",\"playerTypes\":[\"NOPE\"]"))
                .andExpect(status().isBadRequest());
        complete(user, onboarding(version + 5, "[\"rpg\"]", "[\"pc\"]", "")).andExpect(status().isConflict());

        // Başarısız denemelerin hiçbiri yazmadı.
        assertThat(jdbc.queryForObject("select onboarding_completed_at is null from user_profiles where user_id = ?",
                Boolean.class, user)).isTrue();
    }

    @Test
    void esZamanliIkiTamamlamadaTekSonuc() throws Exception {
        UUID user = newUser();
        long version = onboardingVersion(user);

        List<Integer> codes = parallel(
                () -> complete(user, onboarding(version, "[\"rpg\"]", "[\"pc\"]", "")).andReturn().getResponse()
                        .getStatus(),
                () -> complete(user, onboarding(version, "[\"fps\"]", "[\"ps\"]", "")).andReturn().getResponse()
                        .getStatus());

        assertThat(codes).contains(200).allMatch(code -> code == 200 || code == 409);
        assertThat(jdbc.queryForObject("select count(*) from user_profiles where user_id = ? and "
                + "onboarding_completed_at is not null", Integer.class, user)).isEqualTo(1);
    }

    // ------------------------------------------------------------ yardımcılar

    private long onboardingVersion(UUID user) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(ONBOARDING).header(HttpHeaders.AUTHORIZATION,
                bearer(user))).andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.version")).longValue();
    }

    private ResultActions put(UUID user, String body) throws Exception {
        return send(MockMvcRequestBuilders.put(PREFS), user, body);
    }

    private ResultActions complete(UUID user, String body) throws Exception {
        return send(MockMvcRequestBuilders.post(COMPLETE), user, body);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID user, String body) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String prefs(long version, String locale, String theme) {
        return "{\"version\":%d,\"locale\":\"%s\",\"theme\":\"%s\",\"reducedMotion\":false,\"compactLayout\":false}"
                .formatted(version, locale, theme);
    }

    private static String onboarding(long version, String genres, String platforms, String extra) {
        return "{\"version\":%d,\"favoriteGenres\":%s,\"favoritePlatforms\":%s%s}".formatted(version, genres,
                platforms, extra);
    }

    private int rows(String table, UUID user) {
        return jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, user);
    }

    @SafeVarargs
    private static List<Integer> parallel(Callable<Integer>... tasks) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.length)) {
            List<Future<Integer>> futures = Arrays.stream(tasks).map(pool::submit).toList();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
