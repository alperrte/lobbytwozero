package com.ltz.user.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/** Yönetim paneli profil araması: yalnız ADMIN, hesap durumundan bağımsız, yalnız ad ve durum döner. */
class AdminProfileSearchTest extends IntegrationTestSupport {

    @Test
    void adminAtIsaretliKullaniciAdiylaBulurVeUserIdDoner() throws Exception {
        String prefix = "a" + PublicProfileControllerTest.suffix();
        UUID target = newUser();
        insertNamedProfile(target, prefix + "_ada", "Ada Lovelace", "ACTIVE");

        search(adminBearer(), "@" + prefix.toUpperCase()).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(target.toString()))
                .andExpect(jsonPath("$.items[0].username").value(prefix + "_ada"))
                .andExpect(jsonPath("$.items[0].displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.items[0].accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].bio").doesNotExist());
    }

    @Test
    void askidakiYasakliVeSilinmeSurecindekiHesaplariDaBulur() throws Exception {
        String prefix = "d" + PublicProfileControllerTest.suffix();
        insertNamedProfile(newUser(), prefix + "a", "Ad", "ACTIVE");
        insertNamedProfile(newUser(), prefix + "b", "Ad", "SUSPENDED");
        insertNamedProfile(newUser(), prefix + "c", "Ad", "BANNED");
        insertNamedProfile(newUser(), prefix + "d", "Ad", "DELETION_REQUESTED");

        search(adminBearer(), prefix).andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[*].accountStatus",
                        Matchers.contains("ACTIVE", "SUSPENDED", "BANNED", "DELETION_REQUESTED")));
    }

    @Test
    void engelIliskisiSonucuGizlemez() throws Exception {
        String prefix = "e" + PublicProfileControllerTest.suffix();
        UUID admin = newUser();
        UUID blocked = newUser();
        insertNamedProfile(blocked, prefix + "x", "Ad", "ACTIVE");
        block(admin, blocked);

        search(adminBearer(admin), prefix).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void enCokYirmiSonucDonerVeLikeOzelKarakterleriJokerDegildir() throws Exception {
        String prefix = "l" + PublicProfileControllerTest.suffix();
        for (int i = 0; i < 25; i++) {
            insertNamedProfile(newUser(), prefix + String.format("%02d", i), "Ad", "ACTIVE");
        }

        search(adminBearer(), prefix).andExpect(jsonPath("$.items.length()").value(20));
        search(adminBearer(), "%" + prefix).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void gecersizSorgu400() throws Exception {
        search(adminBearer(), "a").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
        search(adminBearer(), "@").andExpect(status().isBadRequest());
        search(adminBearer(), "   ").andExpect(status().isBadRequest());
        mockMvc.perform(get(UserApiPaths.ADMIN_PROFILES_SEARCH).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminOlmayanRolVeMisafirErisemez() throws Exception {
        String q = "abc" + PublicProfileControllerTest.suffix();
        // Oyuncu ve MODERATOR (bu uç yalnız ADMIN): 403.
        search(bearer(newUser()), q).andExpect(status().isForbidden());
        search(bearerWithRoles(newUser(), List.of("MODERATOR")), q).andExpect(status().isForbidden());
        search(bearerWithRoles(newUser(), List.of("PLAYER", "CONTENT_CREATOR")), q).andExpect(status().isForbidden());
        // Misafir: 401.
        mockMvc.perform(get(UserApiPaths.ADMIN_PROFILES_SEARCH).param("q", q)).andExpect(status().isUnauthorized());
    }

    @Test
    void herkeseAcikAramaYoluAdminYetkisiVermez() throws Exception {
        // Aynı sorgu halka açık uçta askıdaki hesabı vermez; yalnız admin ucu verir.
        String prefix = "p" + PublicProfileControllerTest.suffix();
        insertNamedProfile(newUser(), prefix, "Ad", "SUSPENDED");

        mockMvc.perform(get(UserApiPaths.SEARCH).param("q", prefix)
                .header(HttpHeaders.AUTHORIZATION, adminBearer())).andExpect(jsonPath("$.items.length()").value(0));
        search(adminBearer(), prefix).andExpect(jsonPath("$.items.length()").value(1));
    }

    private ResultActions search(String authorization, String q) throws Exception {
        return mockMvc.perform(get(UserApiPaths.ADMIN_PROFILES_SEARCH).param("q", q)
                .header(HttpHeaders.AUTHORIZATION, authorization));
    }

    private static String adminBearer() {
        return adminBearer(newUser());
    }

    private static String adminBearer(UUID admin) {
        return bearerWithRoles(admin, List.of("ADMIN"));
    }

    private static String bearerWithRoles(UUID user, List<String> roles) {
        return "Bearer " + accessToken(user, claims -> claims.claim("roles", roles));
    }
}
