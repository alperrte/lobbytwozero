package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/** Kendi gizlilik ayarlarım (USER-06): varsayılanlar, güncelleme, sürüm, doğrulama, kimlik. */
class PrivacyControllerTest extends IntegrationTestSupport {

    private static final String ALL_PUBLIC = """
            {"version":%d,"friendsList":"PUBLIC","library":"PUBLIC","hardware":"PUBLIC","performance":"PUBLIC",
             "guestProfileAccess":"DETAILED"}""";

    @Test
    void kayitYokkenVarsayilanlarDoner() throws Exception {
        UUID user = newUser();

        read(user).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.friendsList").value("FRIENDS"))
                .andExpect(jsonPath("$.library").value("PUBLIC"))
                .andExpect(jsonPath("$.hardware").value("FRIENDS"))
                .andExpect(jsonPath("$.performance").value("PRIVATE"))
                .andExpect(jsonPath("$.guestProfileAccess").value("BASIC"))
                .andExpect(jsonPath("$.version").value(0));

        assertThat(jdbc.queryForObject("select count(*) from privacy_settings where user_id = ?", Integer.class, user))
                .as("okuma satır oluşturmaz").isZero();
    }

    @Test
    void ilkKayitVeGuncellemeSurumArtirir() throws Exception {
        UUID user = newUser();

        write(user, ALL_PUBLIC.formatted(0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.friendsList").value("PUBLIC"))
                .andExpect(jsonPath("$.guestProfileAccess").value("DETAILED"))
                .andExpect(jsonPath("$.version").value(0));
        write(user, """
                {"version":0,"friendsList":"PRIVATE","library":"FRIENDS","hardware":"PRIVATE","performance":"PRIVATE",
                 "guestProfileAccess":"BASIC"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendsList").value("PRIVATE"))
                .andExpect(jsonPath("$.version").value(1));

        read(user).andExpect(jsonPath("$.library").value("FRIENDS")).andExpect(jsonPath("$.version").value(1));
        assertThat(profileCount(user)).as("profil yoksa açılır (FK)").isEqualTo(1);
    }

    @Test
    void ayniDegerlerSurumuArtirmaz() throws Exception {
        UUID user = newUser();
        write(user, ALL_PUBLIC.formatted(0)).andExpect(status().isOk());

        write(user, ALL_PUBLIC.formatted(0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void eskiSurum409() throws Exception {
        UUID user = newUser();
        write(user, ALL_PUBLIC.formatted(0)).andExpect(status().isOk());
        write(user, ALL_PUBLIC.replace("\"PUBLIC\",\"library\"", "\"FRIENDS\",\"library\"").formatted(0))
                .andExpect(status().isOk());

        write(user, ALL_PUBLIC.formatted(0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        write(newUser(), ALL_PUBLIC.formatted(3)).andExpect(status().isConflict());
    }

    @Test
    void eszamanliIlkKayitlardanYalnizBiriBasarili() throws Exception {
        UUID user = newUser();
        Callable<Integer> first = () -> write(user, ALL_PUBLIC.formatted(0)).andReturn().getResponse().getStatus();

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            for (var result : pool.invokeAll(List.of(first, first, first, first))) {
                assertThat(result.get()).isIn(200, 409);
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from privacy_settings where user_id = ?", Integer.class, user))
                .isEqualTo(1);
    }

    @Test
    void eksikTanimsizVeGecersizDegerler400() throws Exception {
        UUID user = newUser();

        write(user, "{\"version\":0,\"friendsList\":\"PUBLIC\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        write(user, ALL_PUBLIC.formatted(0).replace("\"DETAILED\"", "\"EVERYONE\"")).andExpect(status().isBadRequest());
        write(user, ALL_PUBLIC.formatted(0).replace("\"version\"", "\"userId\":\"%s\",\"version\"".formatted(newUser())))
                .andExpect(status().isBadRequest());
        write(user, ALL_PUBLIC.formatted(-1)).andExpect(status().isBadRequest());

        read(user).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.guestProfileAccess").value("BASIC"));
    }

    @Test
    void kimlikZorunluServisTokeniGecmez() throws Exception {
        mockMvc.perform(get(UserApiPaths.ME_PRIVACY)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(UserApiPaths.ME_PRIVACY).contentType(MediaType.APPLICATION_JSON)
                .content(ALL_PUBLIC.formatted(0))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(UserApiPaths.ME_PRIVACY).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + serviceToken(newUser(), UnaryOperator.identity()))).andExpect(status().isUnauthorized());
    }

    private ResultActions read(UUID user) throws Exception {
        return mockMvc.perform(get(UserApiPaths.ME_PRIVACY).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions write(UUID user, String body) throws Exception {
        return mockMvc.perform(put(UserApiPaths.ME_PRIVACY).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
