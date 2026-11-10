package com.ltz.user.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Auth → User dil okuma ucu (MAIL-AUTH-REQ-02, {@code GET /internal/v1/users/{userId}/locale}): doğru token 200 ve yalnız
 * dil; yanlış çağıran, kapsam ya da hesap 403; tokensiz ve kullanıcı token'ı 401; kayıt yoksa 404.
 */
class InternalLocaleTest extends IntegrationTestSupport {

    private static final String LOCALE_SCOPE = "preferences:locale:read";

    // ------------------------------------------------------------ başarılı okuma

    @Test
    void dogruTokenYalnizDiliDoner() throws Exception {
        UUID user = newUser();
        savePreferences(user, "en", "DARK");

        read(user, localeToken(user)).andExpect(status().isOk())
                .andExpect(content().json("{\"locale\":\"en\"}", true))
                .andExpect(jsonPath("$.theme").doesNotExist())
                .andExpect(jsonPath("$.reducedMotion").doesNotExist())
                .andExpect(jsonPath("$.compactLayout").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void turkceTercihTrDoner() throws Exception {
        UUID user = newUser();
        savePreferences(user, "tr", "LIGHT");

        read(user, localeToken(user)).andExpect(status().isOk()).andExpect(jsonPath("$.locale").value("tr"));
    }

    @Test
    void kullanicininSonDegisikligiOkunur() throws Exception {
        UUID user = newUser();
        savePreferences(user, "tr", "SYSTEM");
        mockMvc.perform(put(UserApiPaths.ME_PREFERENCES).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"locale\":\"en\",\"theme\":\"SYSTEM\",\"reducedMotion\":false,\"compactLayout\":false}"))
                .andExpect(status().isOk());

        read(user, localeToken(user)).andExpect(jsonPath("$.locale").value("en"));
    }

    // ------------------------------------------------------------ kayıt yok

    @Test
    void olmayanKullanici404() throws Exception {
        UUID user = newUser();

        read(user, localeToken(user)).andExpect(status().isNotFound());
    }

    @Test
    void profilVarTercihYoksa404VarsayilanDilUydurulmaz() throws Exception {
        UUID user = newUser();
        insertProfile(user);

        read(user, localeToken(user)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ yetki (403)

    @Test
    void baskaCagiranSub403() throws Exception {
        UUID user = newUser();
        savePreferences(user, "en", "DARK");

        read(user, token("svc:social-service", LOCALE_SCOPE, user)).andExpect(status().isForbidden());
        read(user, token("svc:mail-service", LOCALE_SCOPE, user)).andExpect(status().isForbidden());
    }

    @Test
    void yanlisKapsam403() throws Exception {
        UUID user = newUser();
        savePreferences(user, "en", "DARK");

        read(user, token(AUTH_SUBJECT, ERASE_SCOPE, user)).andExpect(status().isForbidden());
        read(user, token(AUTH_SUBJECT, EVENTS_SCOPE, user)).andExpect(status().isForbidden());
        read(user, token(AUTH_SUBJECT, "privacy:read", user)).andExpect(status().isForbidden());
    }

    @Test
    void baskaHesabinTokeniVeEksikAccountId403() throws Exception {
        UUID user = newUser();
        savePreferences(user, "en", "DARK");

        read(user, localeToken(newUser())).andExpect(status().isForbidden());
        read(user, sign(SIGNING_KEY, SERVICE_TOKEN_TYPE,
                serviceClaims(AUTH_SUBJECT, LOCALE_SCOPE).build())).andExpect(status().isForbidden());
    }

    @Test
    void yetkisizIstekDilBilgisiSizdirmaz() throws Exception {
        UUID user = newUser();
        savePreferences(user, "en", "DARK");

        read(user, token("svc:social-service", LOCALE_SCOPE, user))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"en\""))));
    }

    // ------------------------------------------------------------ kimlik (401)

    @Test
    void tokensiz401() throws Exception {
        mockMvc.perform(get(path(newUser()))).andExpect(status().isUnauthorized());
    }

    @Test
    void kullaniciTokeniIcUcta401() throws Exception {
        UUID user = newUser();
        savePreferences(user, "en", "DARK");

        read(user, accessToken(user, UnaryOperator.identity())).andExpect(status().isUnauthorized());
    }

    @Test
    void sureAsimiVeYanlisAudience401() throws Exception {
        UUID user = newUser();
        savePreferences(user, "en", "DARK");

        read(user, sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, serviceClaims(AUTH_SUBJECT, LOCALE_SCOPE)
                .claim("account_id", user.toString()).audience("library-service").build()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uuidOlmayanYol400() throws Exception {
        mockMvc.perform(get("/internal/v1/users/not-a-uuid/locale").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + localeToken(newUser()))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ yardımcılar

    private ResultActions read(UUID user, String token) throws Exception {
        return mockMvc.perform(get(path(user)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String path(UUID user) {
        return "/internal/v1/users/" + user + "/locale";
    }

    private static String localeToken(UUID user) {
        return token(AUTH_SUBJECT, LOCALE_SCOPE, user);
    }

    private static String token(String subject, String scope, UUID accountId) {
        JWTClaimsSet claims = serviceClaims(subject, scope).claim("account_id", accountId.toString()).build();
        return sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, claims);
    }

    private void savePreferences(UUID user, String locale, String theme) {
        insertProfile(user);
        jdbc.update("""
                insert into user_preferences (user_id, locale, theme, reduced_motion, compact_layout, updated_at, version)
                values (?, ?, ?, true, true, now(), 0)""", user, locale, theme);
    }
}
