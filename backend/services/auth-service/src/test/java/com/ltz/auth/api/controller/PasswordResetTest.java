package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;

@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetTest extends IntegrationTestSupport {

    private static final String NEW_PASSWORD = "reset-passphrase-with-length-1";
    private static final int MAX_REQUESTS = 5;
    private static final String PAST_COOLDOWN = "2 minutes";

    @Autowired
    private RecordingMailPort mailPort;

    @Test
    void bilinenVeBilinmeyenHesapAyniYaniti_alir_yalnizBilinenHesaba_epostaGider() throws Exception {
        String known = registerActiveAccount(uniqueEmail(), uniqueUsername());
        String unknown = uniqueEmail();

        String knownBody = forgot(known).andExpect(status().isAccepted())
                .andExpect(content().json("{\"nextStep\":\"CHECK_EMAIL\"}", true))
                .andReturn().getResponse().getContentAsString();
        String unknownBody = forgot(unknown).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(unknownBody).isEqualTo(knownBody);
        mailPort.awaitLatestResetToken(known, 1);
        assertThat(mailPort.resetsTo(unknown)).isEmpty();
    }

    @Test
    void tokenYuksekEntropiliVeYalnizOzetOlarakSaklanir_baglantiFragmentTasir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());

        forgot(email).andExpect(status().isAccepted());

        String token = mailPort.awaitLatestResetToken(email, 1);
        assertThat(token).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(mailPort.resetsTo(email).getFirst().resetLink()).contains("/auth/reset-password#token=");
        assertThat(jdbc.queryForObject("select token_hash from password_reset_tokens where account_id = ?", String.class,
                accountId(email))).hasSize(64).isNotEqualTo(token).doesNotContain(token);
        assertThat(jdbc.queryForObject("select expires_at - created_at from password_reset_tokens where account_id = ?",
                String.class, accountId(email))).startsWith("00:30:00");
    }

    @Test
    void uygunOlmayanHesaplaraEpostaGitmez() throws Exception {
        String pending = uniqueEmail();
        register(pending, uniqueUsername(), VALID_PASSWORD).andExpect(status().isAccepted());
        String suspended = registerActiveAccount(uniqueEmail(), uniqueUsername());
        jdbc.update("update accounts set status = 'SUSPENDED' where email_normalized = lower(?)", suspended);
        String passwordless = registerActiveAccount(uniqueEmail(), uniqueUsername());
        jdbc.update("update accounts set password_hash = null where email_normalized = lower(?)", passwordless);

        for (String email : List.of(pending, suspended, passwordless)) {
            forgot(email).andExpect(status().isAccepted()).andExpect(jsonPath("$.nextStep").value("CHECK_EMAIL"));
            assertThat(mailPort.resetsTo(email)).as(email).isEmpty();
        }
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens t join accounts a on a.id = t.account_id "
                + "where a.email_normalized in (lower(?), lower(?), lower(?))", Integer.class, pending, suspended,
                passwordless)).isZero();
    }

    @Test
    void beklemeSuresindeIkinciIstekEpostaUretmez_sonrasindaEskiTokenGecersizlesir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        forgot(email).andExpect(status().isAccepted());
        String first = mailPort.awaitLatestResetToken(email, 1);

        forgot(email).andExpect(status().isAccepted());
        assertThat(mailPort.resetsTo(email)).hasSize(1);

        shiftTokensBack(accountId, PAST_COOLDOWN);
        forgot(email).andExpect(status().isAccepted());
        String second = mailPort.awaitLatestResetToken(email, 2);

        assertThat(second).isNotEqualTo(first);
        reset(first, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where account_id = ? "
                + "and invalidation_reason = 'SUPERSEDED'", Integer.class, accountId)).isEqualTo(1);
        reset(second, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void istekSayisiHesapVarligindanBagimsizSinirlanir() throws Exception {
        String unknown = uniqueEmail();
        for (int request = 0; request < MAX_REQUESTS; request++) {
            forgot(unknown).andExpect(status().isAccepted());
        }

        forgot(unknown).andExpect(status().isTooManyRequests()).andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        String known = registerActiveAccount(uniqueEmail(), uniqueUsername());
        for (int request = 0; request < MAX_REQUESTS; request++) {
            forgot(known).andExpect(status().isAccepted());
        }
        forgot(known).andExpect(status().isTooManyRequests());
    }

    @Test
    void zayifYeniParolaReddedilirTokenTuketilmezVeGucluParolayaIzinVerir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        forgot(email).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestResetToken(email, 1);
        String hashBefore = jdbc.queryForObject("select password_hash from accounts where id = ?", String.class,
                accountId(email));

        reset(token, "passwordpassword1", "passwordpassword1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));

        assertThat(jdbc.queryForObject("select password_hash from accounts where id = ?", String.class,
                accountId(email))).isEqualTo(hashBefore);
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void sifirlamaParolayiDegistirir_oturumlariIptalEder_otomatikGirisYapmaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        SessionCookiePair session = loginSession(email);
        forgot(email).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestResetToken(email, 1);

        List<String> cookies = reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        // Oturum açılmaz: yalnız (gizli olmayan) CSRF cookie'si yazılabilir.
        assertThat(cookies).noneMatch(cookie -> cookie.startsWith(ACCESS_COOKIE) || cookie.startsWith(REFRESH_COOKIE));

        UUID accountId = accountId(email);
        assertThat(jdbc.queryForObject("select password_hash from accounts where id = ?", String.class, accountId))
                .startsWith("{argon2}$argon2id$").doesNotContain(NEW_PASSWORD);
        assertThat(jdbc.queryForObject("select revoke_reason from auth_sessions where account_id = ?", String.class,
                accountId)).isEqualTo("PASSWORD_RESET");
        refresh(session.refresh()).andExpect(status().isUnauthorized());
        login(email, VALID_PASSWORD).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        assertThat(securityEventCount(accountId, "PASSWORD_RESET_COMPLETED")).isEqualTo(1);
    }

    @Test
    void sifirlama2faYiKapatmaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        enableTotp(accountId);
        forgot(email).andExpect(status().isAccepted());

        reset(mailPort.awaitLatestResetToken(email, 1), NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select status from totp_credentials where account_id = ?", String.class,
                accountId)).isEqualTo("ACTIVE");
        login(email, NEW_PASSWORD).andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
    }

    @Test
    void tokenTekKullanimlikSuresiDolanVeSahteTokenReddedilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        forgot(email).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestResetToken(email, 1);

        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(token, "second-use-passphrase-1", "second-use-passphrase-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
        reset("forged-" + uniqueSuffix(), NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());

        String expired = registerActiveAccount(uniqueEmail(), uniqueUsername());
        forgot(expired).andExpect(status().isAccepted());
        String expiredToken = mailPort.awaitLatestResetToken(expired, 1);
        shiftTokensBack(accountId(expired), "31 minutes");
        reset(expiredToken, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
        login(expired, VALID_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void gecersizYeniParolaTokeniTuketmez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        forgot(email).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestResetToken(email, 1);

        reset(token, "short", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        reset(token, NEW_PASSWORD, NEW_PASSWORD + "x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPasswordConfirmed"));

        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void eszamanliIkiSifirlamadanYalnizBiriBasarili() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        forgot(email).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestResetToken(email, 1);

        List<Integer> statuses = ConcurrentRequests.run(4, index -> () -> reset(token,
                "concurrent-reset-" + index + "-passphrase", "concurrent-reset-" + index + "-passphrase")
                .andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(code -> code == 204)).hasSize(1);
        assertThat(statuses.stream().filter(code -> code == 400)).hasSize(3);
    }

    @Test
    void hesapSifirlamadanOnceAskiyaAlinirsaTokenKullanilamaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        forgot(email).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestResetToken(email, 1);
        jdbc.update("update accounts set status = 'BANNED' where email_normalized = lower(?)", email);

        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void tokenVeParolalarLoglaraYazilmaz(CapturedOutput output) throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        forgot(email).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestResetToken(email, 1);
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(output.getAll()).doesNotContain(token).doesNotContain(NEW_PASSWORD).doesNotContain(VALID_PASSWORD)
                .doesNotContainIgnoringCase(email);
    }

    private ResultActions forgot(String email) throws Exception {
        return postJson(AuthApiPaths.PASSWORD_FORGOT, "{\"email\":\"%s\"}".formatted(email));
    }

    private ResultActions reset(String token, String password, String confirmation) throws Exception {
        return postJson(AuthApiPaths.PASSWORD_RESET, "{\"token\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirmation\":\"%s\"}"
                .formatted(token, password, confirmation));
    }

    private void shiftTokensBack(UUID accountId, String interval) {
        jdbc.update("update password_reset_tokens set created_at = created_at - cast(? as interval), "
                + "expires_at = expires_at - cast(? as interval) where account_id = ?", interval, interval, accountId);
    }

}
