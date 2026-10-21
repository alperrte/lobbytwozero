package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;

import jakarta.servlet.http.Cookie;

@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PasswordChangeTest extends IntegrationTestSupport {

    private static final String NEW_PASSWORD = "another-long-passphrase-42";
    private static final int MAX_FAILURES = 5;

    @Test
    void parolaDegisirTumOturumlarKapanir_eskiParolaGecersizYeniGecerli(CapturedOutput output) throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        SessionCookiePair device = loginSession(email);
        SessionCookiePair otherDevice = loginSession(email);

        change(device.access(), VALID_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        UUID accountId = accountId(email);
        assertThat(jdbc.queryForObject("select password_hash from accounts where id = ?", String.class, accountId))
                .startsWith("{argon2}$argon2id$").doesNotContain(NEW_PASSWORD);
        refresh(device.refresh()).andExpect(status().isUnauthorized());
        refresh(otherDevice.refresh()).andExpect(status().isUnauthorized());
        change(otherDevice.access(), NEW_PASSWORD, "yet-another-passphrase-7", "yet-another-passphrase-7")
                .andExpect(status().isUnauthorized());
        login(email, VALID_PASSWORD).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        assertThat(securityEventCount(accountId, "PASSWORD_CHANGED")).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(VALID_PASSWORD).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void cookieleriSilerVeIstegiGizliBilgiDondurmez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        Cookie access = loginSession(email).access();

        var result = change(access, VALID_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getCookie(ACCESS_COOKIE).getMaxAge()).isZero();
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE).getMaxAge()).isZero();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void yanlisMevcutParolaVeAyniParolaReddedilir_hesapDegismez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        Cookie access = loginSession(email).access();
        String hashBefore = hash(email);

        change(access, "not-the-current-password", NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        change(access, VALID_PASSWORD, VALID_PASSWORD, VALID_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NEW_PASSWORD_SAME_AS_CURRENT"));

        assertThat(hash(email)).isEqualTo(hashBefore);
        assertThat(securityEventCount(accountId(email), "PASSWORD_CHANGE_FAILED")).isEqualTo(1);
        change(access, VALID_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void parolaPolitikasiVeTekrarKontroluUygulanir() throws Exception {
        Cookie access = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername())).access();

        change(access, VALID_PASSWORD, "short", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        change(access, VALID_PASSWORD, "a".repeat(129), "a".repeat(129)).andExpect(status().isBadRequest());
        change(access, VALID_PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPasswordConfirmed"));
        // Boşluklu parola olduğu gibi kabul edilir ve dönüştürülmez.
        String spaced = "  spaced passphrase 99  ";
        change(access, VALID_PASSWORD, spaced, spaced).andExpect(status().isNoContent());
    }

    @Test
    void kimliksizIstek401_csrfsizCookieIstegi403() throws Exception {
        Cookie access = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername())).access();
        String body = body(VALID_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        mockMvc.perform(post(AuthApiPaths.PASSWORD_CHANGE).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(AuthApiPaths.PASSWORD_CHANGE).cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void mevcutParolaTahminiHesapBazindaSinirlanir() throws Exception {
        Cookie access = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername())).access();
        for (int attempt = 0; attempt < MAX_FAILURES; attempt++) {
            change(access, "wrong-password-" + attempt, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        change(access, VALID_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void parolasizHarciHesapParolaDegistiremez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        Cookie access = loginSession(email).access();
        jdbc.update("update accounts set password_hash = null where email_normalized = lower(?)", email);

        change(access, VALID_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
    }

    @Test
    void eszamanliIkiDegisiklikteYalnizBiriBasarili() throws Exception {
        Cookie access = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername())).access();

        List<Integer> statuses = ConcurrentRequests.run(2, index -> () -> change(access, VALID_PASSWORD,
                "concurrent-passphrase-" + index + "-xyz", "concurrent-passphrase-" + index + "-xyz")
                .andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(204, 400);
    }

    @Test
    void degisiklikBekleyenSifirlamaTokenlariniGecersizKilar() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        jdbc.update("insert into password_reset_tokens (id, account_id, token_hash, created_at, expires_at) "
                + "values (gen_random_uuid(), ?, repeat('a', 64), now(), now() + interval '30 minutes')", accountId);

        change(loginSession(email).access(), VALID_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select invalidation_reason from password_reset_tokens where account_id = ?",
                String.class, accountId)).isEqualTo("PASSWORD_CHANGED");
    }

    private ResultActions change(Cookie access, String current, String next, String confirmation) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.PASSWORD_CHANGE).cookie(access).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(current, next, confirmation)));
    }

    private static String body(String current, String next, String confirmation) {
        return "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirmation\":\"%s\"}"
                .formatted(current, next, confirmation);
    }

    private String hash(String email) {
        return jdbc.queryForObject("select password_hash from accounts where email_normalized = lower(?)",
                String.class, email);
    }
}
