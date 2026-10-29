package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ltz.auth.IntegrationTestSupport;

class SecurityRulesTest extends IntegrationTestSupport {

    @Test
    void healthKimlikDogrulamasizErisilebilirAyrintiVermez() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void healthDisindakiActuatorUclariKapalidir() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void tanimsizUcKimliksizIstekte401ProblemDetailDoner() throws Exception {
        mockMvc.perform(get(AuthApiPaths.BASE + "/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void kimligiOlsaBileAcikcaIzinVerilmeyenUc403Doner() throws Exception {
        mockMvc.perform(get(AuthApiPaths.BASE + "/accounts/me").with(user("player")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void yanitlarGuvenlikBasliklariTasirOturumCerezOlusturulmaz() throws Exception {
        List<String> cookies = resend(uniqueEmail())
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);

        // Yalnız (gizli olmayan) CSRF cookie'si yazılabilir; oturum veya token cookie'si yazılmaz.
        assertThat(cookies.stream().filter(cookie -> !cookie.startsWith(CSRF_COOKIE + "=")).toList()).isEmpty();
    }
}
