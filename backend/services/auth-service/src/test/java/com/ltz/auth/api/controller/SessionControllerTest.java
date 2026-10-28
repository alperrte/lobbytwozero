package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;

import jakarta.servlet.http.Cookie;

@Import(RecordingMailPortConfiguration.class)
class SessionControllerTest extends IntegrationTestSupport {

    @Test
    void refreshTokeniDondururVeYeniCookieYazar() throws Exception {
        SessionCookiePair session = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        MvcResult refreshed = refresh(session.refresh())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andReturn();

        SessionCookiePair rotated = SessionCookiePair.from(refreshed);
        assertThat(rotated.refresh().getValue()).isNotEqualTo(session.refresh().getValue());
        assertThat(rotated.access().getValue()).isNotEqualTo(session.access().getValue());
        refresh(rotated.refresh()).andExpect(status().isOk());
    }

    @Test
    void donmusTokenKisaSureIcindeTekrarGelirse409DonerOturumKorunur() throws Exception {
        SessionCookiePair session = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        SessionCookiePair rotated = SessionCookiePair.from(refresh(session.refresh()).andReturn());

        refresh(session.refresh()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFRESH_CONFLICT"));

        refresh(rotated.refresh()).andExpect(status().isOk());
    }

    @Test
    void sureSonrasiTekrarKullanimTumOturumAilesiniIptalEder() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        SessionCookiePair session = loginSession(email);
        SessionCookiePair rotated = SessionCookiePair.from(refresh(session.refresh()).andReturn());
        jdbc.update("update refresh_tokens set rotated_at = rotated_at - interval '1 minute' where rotated_at is not null "
                + "and session_id in (select id from auth_sessions where account_id = ?)", accountId(email));

        refresh(session.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        refresh(rotated.refresh()).andExpect(status().isUnauthorized());
        UUID accountId = accountId(email);
        assertThat(securityEventCount(accountId, "REFRESH_TOKEN_REUSE_DETECTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select revoke_reason from auth_sessions where account_id = ?", String.class,
                accountId)).isEqualTo("REFRESH_REUSE");
    }

    @Test
    void eszamanliIkiRefreshtenYalnizBiriDondurur() throws Exception {
        SessionCookiePair session = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        List<Integer> statuses = ConcurrentRequests.run(2,
                index -> () -> refresh(session.refresh()).andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    }

    @Test
    void logoutSonrasiRefreshReddedilirCookielerTemizlenirTekrarCagriGuvenlidir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        SessionCookiePair session = loginSession(email);

        MvcResult logout = mockMvc.perform(post(AuthApiPaths.SESSION_LOGOUT).with(xsrf()).cookie(session.refresh()))
                .andExpect(status().isNoContent()).andReturn();

        assertThat(logout.getResponse().getCookie(ACCESS_COOKIE).getMaxAge()).isZero();
        assertThat(logout.getResponse().getCookie(REFRESH_COOKIE).getMaxAge()).isZero();
        refresh(session.refresh()).andExpect(status().isUnauthorized());
        mockMvc.perform(post(AuthApiPaths.SESSION_LOGOUT).with(xsrf()).cookie(session.refresh()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(AuthApiPaths.TOTP_SETUP).with(xsrf()).cookie(session.access()))
                .andExpect(status().isUnauthorized());
        assertThat(securityEventCount(accountId(email), "LOGOUT")).isEqualTo(1);
    }

    @Test
    void tumCihazlardanCikisTumOturumlariVeEskiAccessTokenlariGecersizKilar() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        SessionCookiePair first = loginSession(email);
        SessionCookiePair second = loginSession(email);

        mockMvc.perform(post(AuthApiPaths.SESSION_LOGOUT_ALL).with(xsrf()).cookie(first.access()))
                .andExpect(status().isNoContent());

        refresh(first.refresh()).andExpect(status().isUnauthorized());
        refresh(second.refresh()).andExpect(status().isUnauthorized());
        mockMvc.perform(post(AuthApiPaths.TOTP_SETUP).with(xsrf()).cookie(second.access()))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select token_version from accounts where id = ?", Long.class,
                accountId(email))).isEqualTo(1L);
    }

    @Test
    void hesapAskiyaAlinincaRefreshVeAccessTokenAnindaReddedilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        SessionCookiePair session = loginSession(email);
        jdbc.update("update accounts set status = 'SUSPENDED' where email_normalized = lower(?)", email);

        mockMvc.perform(post(AuthApiPaths.TOTP_SETUP).with(xsrf()).cookie(session.access()))
                .andExpect(status().isUnauthorized());
        refresh(session.refresh()).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select revoke_reason from auth_sessions where account_id = ?", String.class,
                accountId(email))).isEqualTo("ACCOUNT_INACTIVE");
    }

    @Test
    void suresiDolmusRefreshTokenReddedilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        SessionCookiePair session = loginSession(email);
        jdbc.update("update refresh_tokens set created_at = created_at - interval '8 days', "
                + "expires_at = expires_at - interval '8 days' "
                + "where session_id in (select id from auth_sessions where account_id = ?)", accountId(email));

        refresh(session.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshCsrfTokenIsterCookieYoksa401() throws Exception {
        SessionCookiePair session = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        mockMvc.perform(post(AuthApiPaths.SESSION_REFRESH).cookie(session.refresh()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(AuthApiPaths.SESSION_REFRESH).with(xsrf()))
                .andExpect(status().isUnauthorized());
        refresh(new Cookie(REFRESH_COOKIE, "forged-" + uniqueSuffix())).andExpect(status().isUnauthorized());
    }

    @Test
    void korumaliUcAccessCookieIleCsrfOlmadanReddedilir() throws Exception {
        SessionCookiePair session = loginSession(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        mockMvc.perform(post(AuthApiPaths.SESSION_LOGOUT_ALL).cookie(session.access()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }
}
