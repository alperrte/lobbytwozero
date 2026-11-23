package com.ltz.user.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/**
 * Hesap kullanılabilirliği ucu ({@code POST /internal/v1/users/status}): yalnız profili olan ve hesabı {@code ACTIVE} olan
 * kullanıcı {@code ACTIVE}; diğer her durum ve profil yokluğu {@code UNAVAILABLE}. Yalnız messaging ve social servis
 * token'ı; kullanıcı token'ı 401; kapsam ya da çağıran uyuşmazlığı 403; en çok 100 kimlik.
 */
class UserStatusTest extends IntegrationTestSupport {

    private static final String SCOPE = "users:status:read";
    private static final String MESSAGING = "messaging-service";
    private static final String SOCIAL = "social-service";
    private static final String UNAVAILABLE = "UNAVAILABLE";

    // ------------------------------------------------------------ karar

    @Test
    void yalnizEtkinHesapActiveDigerHerDurumUnavailable() throws Exception {
        UUID active = profile("ACTIVE");
        List<UUID> others = List.of(profile("SUSPENDED"), profile("BANNED"), profile("DISABLED"),
                profile("PENDING_VERIFICATION"), profile("DELETION_REQUESTED"), profile("DELETED"));
        List<UUID> ids = new ArrayList<>(List.of(active));
        ids.addAll(others);

        ResultActions result = ask(MESSAGING, ids).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("ACTIVE"));
        for (int i = 1; i < ids.size(); i++) {
            result.andExpect(jsonPath("$.results[" + i + "].status").value(UNAVAILABLE));
        }
    }

    @Test
    void profiliOlmayanHesapUnavailable() throws Exception {
        UUID unknown = newUser();

        ask(SOCIAL, List.of(unknown)).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].userId").value(unknown.toString()))
                .andExpect(jsonPath("$.results[0].status").value(UNAVAILABLE));
    }

    @Test
    void sonucIstekSirasindaYinelenenlerBirKez() throws Exception {
        UUID first = profile("ACTIVE");
        UUID second = newUser();

        ask(MESSAGING, List.of(second, first, second, first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].userId").value(second.toString()))
                .andExpect(jsonPath("$.results[0].status").value(UNAVAILABLE))
                .andExpect(jsonPath("$.results[1].userId").value(first.toString()))
                .andExpect(jsonPath("$.results[1].status").value("ACTIVE"));
    }

    @Test
    void yuzKimlikKabulEdilirYanitOnbellegeAlinmaz() throws Exception {
        List<UUID> ids = new ArrayList<>(List.of(profile("ACTIVE")));
        while (ids.size() < 100) {
            ids.add(newUser());
        }

        ask(SOCIAL, ids).andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(100))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void profilVerisiSizmaz() throws Exception {
        UUID user = newUser();
        insertNamedProfile(user, "gizli_kisi", "Gizli Kisi", "ACTIVE");

        ask(MESSAGING, List.of(user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].username").doesNotExist())
                .andExpect(jsonPath("$.results[0].displayName").doesNotExist())
                .andExpect(content().string(not(containsString("gizli_kisi"))))
                .andExpect(content().string(not(containsString("Gizli Kisi"))));
    }

    // ------------------------------------------------------------ doğrulama (400)

    @Test
    void yuzBirKimlikVeGecersizGovde400() throws Exception {
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(newUser());
        }

        ask(MESSAGING, tooMany).andExpect(status().isBadRequest());
        ask(MESSAGING, List.of()).andExpect(status().isBadRequest());
        perform(token("svc:messaging-service", SCOPE), "{}").andExpect(status().isBadRequest());
        perform(token("svc:messaging-service", SCOPE), "{\"userIds\":[null]}").andExpect(status().isBadRequest());
        perform(token("svc:messaging-service", SCOPE), "{\"userIds\":[\"not-a-uuid\"]}")
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ yetki (403)

    @Test
    void baskaCagiranSub403() throws Exception {
        List<UUID> ids = List.of(profile("ACTIVE"));

        for (String caller : List.of("community-service", "auth-service", "library-service", "hardware-service",
                "lfp-service")) {
            ask(caller, ids).andExpect(status().isForbidden());
        }
    }

    @Test
    void yanlisKapsam403() throws Exception {
        String body = body(List.of(profile("ACTIVE")));

        perform(token("svc:messaging-service", "privacy:read"), body).andExpect(status().isForbidden());
        perform(token("svc:messaging-service", "events:ingest"), body).andExpect(status().isForbidden());
        perform(token("svc:social-service", "account:erase"), body).andExpect(status().isForbidden());
    }

    @Test
    void yetkisizIstekDurumSizdirmaz() throws Exception {
        UUID user = profile("ACTIVE");

        ask("community-service", List.of(user)).andExpect(content().string(not(containsString("ACTIVE"))));
    }

    // ------------------------------------------------------------ kimlik (401)

    @Test
    void tokensizVeKullaniciTokeni401() throws Exception {
        String body = body(List.of(newUser()));

        mockMvc.perform(post(InternalApiPaths.USER_STATUS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(InternalApiPaths.USER_STATUS).header(HttpHeaders.AUTHORIZATION, bearer(newUser()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    void yanlisAudience401() throws Exception {
        String wrongAudience = sign(SIGNING_KEY, SERVICE_TOKEN_TYPE,
                serviceClaims("svc:messaging-service", SCOPE).audience("library-service").build());

        perform(wrongAudience, body(List.of(newUser()))).andExpect(status().isUnauthorized());
    }

    @Test
    void getIleAcilmaz() throws Exception {
        mockMvc.perform(get(InternalApiPaths.USER_STATUS).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token("svc:messaging-service", SCOPE))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ yardımcılar

    private ResultActions ask(String caller, List<UUID> ids) throws Exception {
        return perform(token("svc:" + caller, SCOPE), body(ids));
    }

    private ResultActions perform(String token, String json) throws Exception {
        return mockMvc.perform(post(InternalApiPaths.USER_STATUS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(List<UUID> ids) {
        StringBuilder json = new StringBuilder("{\"userIds\":[");
        for (int i = 0; i < ids.size(); i++) {
            json.append(i == 0 ? "" : ",").append('"').append(ids.get(i)).append('"');
        }
        return json.append("]}").toString();
    }

    private static String token(String subject, String scope) {
        return sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, serviceClaims(subject, scope).build());
    }

    private UUID profile(String status) {
        UUID user = newUser();
        jdbc.update("""
                insert into user_profiles (user_id, display_name, bio, account_status, created_at, updated_at, version)
                values (?, 'Test Oyuncu', 'merhaba', ?, now(), now(), 0)""", user, status);
        return user;
    }
}
