package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import com.ltz.auth.AccountDeletionTestSupport;
import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.RecordingMailPortConfiguration;

import jakarta.servlet.http.Cookie;

@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AccountDeletionTest extends AccountDeletionTestSupport {

    // ---- Yeniden doğrulama (istek adımı) ----

    @Test
    void kimliksiz401_cookieIleCsrfOlmadan403() throws Exception {
        mockMvc.perform(post(AuthApiPaths.ACCOUNT_DELETION_REQUEST).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(passwordJson())).andExpect(status().isUnauthorized());

        String access = bearerFor(accountId(registerActiveAccount(uniqueEmail(), uniqueUsername())))
                .substring("Bearer ".length());
        mockMvc.perform(post(AuthApiPaths.ACCOUNT_DELETION_REQUEST).cookie(new Cookie(ACCESS_COOKIE, access))
                        .contentType(MediaType.APPLICATION_JSON).content(passwordJson()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void yanlisParolaReddedilir_dogruParolaEpostaTokenUretir_hesapHenuzSilinmez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        String bearer = bearerFor(accountId);

        requestDeletion(bearer, "{\"password\":\"wrong-password-value\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
        requestDeletion(bearer, "{}").andExpect(status().isBadRequest());
        assertThat(mailPort.deletionsTo(email)).isEmpty();

        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nextStep").value("CONFIRM_BY_EMAIL"));

        String token = mailPort.awaitLatestDeletionToken(email, 1);
        assertThat(token).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(mailPort.deletionsTo(email).getFirst().confirmationLink()).contains("/account/delete#token=");
        assertThat(jdbc.queryForObject("select token_hash from account_deletion_tokens where account_id = ?",
                String.class, accountId)).hasSize(64).doesNotContain(token);
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_requests where account_id = ?",
                Integer.class, accountId)).isZero();
    }

    @Test
    void totpEtkinseIkinciFaktorZorunlu_kodTekKullanimlik() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        enableTotp(accountId);
        String bearer = bearerFor(accountId);

        requestDeletion(bearer, passwordJson()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));
        requestDeletion(bearer, "{\"password\":\"%s\",\"code\":\"000000\"}".formatted(VALID_PASSWORD))
                .andExpect(status().isBadRequest());
        assertThat(mailPort.deletionsTo(email)).isEmpty();

        String code = currentTotpCode(accountId);
        String json = "{\"password\":\"%s\",\"code\":\"%s\"}".formatted(VALID_PASSWORD, code);
        requestDeletion(bearer, json).andExpect(status().isAccepted());
        requestDeletion(bearer, json).andExpect(status().isBadRequest());
        requestDeletion(bearer, "{\"password\":\"%s\",\"code\":\"%s\",\"recoveryCode\":\"AAAAA-AAAAA\"}"
                .formatted(VALID_PASSWORD, code)).andExpect(status().isBadRequest());
        mailPort.awaitLatestDeletionToken(email, 1);
    }

    @Test
    void parolasizSosyalHesapYalnizYakinZamanliGirisleSilmeIsteyebilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        jdbc.update("update accounts set password_hash = null where id = ?", accountId);
        String staleSession = bearerFor(accountId);
        jdbc.update("update auth_sessions set created_at = created_at - interval '1 hour', "
                + "expires_at = expires_at + interval '1 hour' where account_id = ?", accountId);

        requestDeletion(staleSession, "{}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTHENTICATION_REQUIRED"));
        assertThat(mailPort.deletionsTo(email)).isEmpty();

        requestDeletion(bearerFor(accountId), "{}").andExpect(status().isAccepted());
        mailPort.awaitLatestDeletionToken(email, 1);
    }

    @Test
    void yoneticiHesabiSilinemez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        enableTotp(accountId);
        grantRole(accountId, "ADMIN");

        requestDeletion(bearerFor(accountId), "{\"password\":\"%s\",\"code\":\"%s\"}"
                .formatted(VALID_PASSWORD, currentTotpCode(accountId))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_DELETION_FORBIDDEN"));
    }

    @Test
    void bekleSuresindeIkinciIstekEpostaUretmez_sonrasindaEskiTokenGecersizlesir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        String bearer = bearerFor(accountId);
        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted());
        String first = mailPort.awaitLatestDeletionToken(email, 1);

        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted());
        assertThat(mailPort.deletionsTo(email)).hasSize(1);

        jdbc.update("update account_deletion_tokens set created_at = created_at - interval '2 minutes', "
                + "expires_at = expires_at - interval '2 minutes' where account_id = ?", accountId);
        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted());
        String second = mailPort.awaitLatestDeletionToken(email, 2);

        assertThat(second).isNotEqualTo(first);
        confirmDeletion(bearer, first, usernameOf(accountId)).andExpect(status().isBadRequest());
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");
    }

    // ---- Onay adımı ----

    @Test
    void yanlisKullaniciAdiTokeniTuketmez_dogruOnayHesabiKapatirOturumlariIptalEder() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        SessionCookiePair device = loginSession(email);
        String bearer = bearerFor(accountId);
        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestDeletionToken(email, 1);

        confirmDeletion(bearer, token, "someone_else").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DELETION_CONFIRMATION_INVALID"));
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");

        confirmDeletion(bearer, token, usernameOf(accountId).toUpperCase()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nextStep").value("DELETION_IN_PROGRESS"));

        assertThat(accountStatus(email)).isEqualTo("DELETION_REQUESTED");
        assertThat(jdbc.queryForList("select distinct revoke_reason from auth_sessions where account_id = ?",
                String.class, accountId)).containsExactly("ACCOUNT_DELETION");
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_tasks where account_id = ? "
                + "and status = 'PENDING'", Integer.class, accountId)).isEqualTo(2);
        refresh(device.refresh()).andExpect(status().isUnauthorized());
        confirmDeletion(bearer, token, usernameOf(accountId)).andExpect(status().isUnauthorized());
        login(email, VALID_PASSWORD).andExpect(status().isForbidden());
        assertThat(securityEventCount(accountId, "ACCOUNT_DELETION_CONFIRMED")).isEqualTo(1);
    }

    @Test
    void tokenYalnizIstekYapanHesapIcinGecerli_suresiDolanVeSahteTokenReddedilir() throws Exception {
        String victimEmail = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID victim = accountId(victimEmail);
        requestDeletion(bearerFor(victim), passwordJson()).andExpect(status().isAccepted());
        String victimToken = mailPort.awaitLatestDeletionToken(victimEmail, 1);
        String attackerEmail = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID attacker = accountId(attackerEmail);

        confirmDeletion(bearerFor(attacker), victimToken, usernameOf(victim)).andExpect(status().isBadRequest());
        confirmDeletion(bearerFor(attacker), victimToken, usernameOf(attacker)).andExpect(status().isBadRequest());
        confirmDeletion(bearerFor(victim), "forged-" + uniqueSuffix(), usernameOf(victim)).andExpect(status().isBadRequest());
        assertThat(accountStatus(victimEmail)).isEqualTo("ACTIVE");
        assertThat(accountStatus(attackerEmail)).isEqualTo("ACTIVE");

        jdbc.update("update account_deletion_tokens set created_at = created_at - interval '31 minutes', "
                + "expires_at = expires_at - interval '31 minutes' where account_id = ?", victim);
        confirmDeletion(bearerFor(victim), victimToken, usernameOf(victim)).andExpect(status().isBadRequest());
        assertThat(accountStatus(victimEmail)).isEqualTo("ACTIVE");
    }

    @Test
    void eszamanliIkiOnaydanYalnizBiriBasarili_tekTalepOlusur() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        String bearer = bearerFor(accountId);
        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestDeletionToken(email, 1);
        String username = usernameOf(accountId);

        List<Integer> statuses = ConcurrentRequests.run(3,
                index -> () -> confirmDeletion(bearer, token, username).andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(code -> code == 202)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_requests where account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_tasks where account_id = ?",
                Integer.class, accountId)).isEqualTo(2);
    }

    @Test
    void tokenVeParolaLoglaraYazilmaz(CapturedOutput output) throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        String bearer = bearerFor(accountId);
        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestDeletionToken(email, 1);
        confirmDeletion(bearer, token, usernameOf(accountId)).andExpect(status().isAccepted());

        assertThat(output.getAll()).doesNotContain(token).doesNotContain(VALID_PASSWORD)
                .doesNotContainIgnoringCase(email);
    }
}
