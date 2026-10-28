package com.ltz.auth.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.ltz.auth.IntegrationTestSupport;

/** Varsayılan yapılandırmada Google kimlik bilgisi yoktur: Google girişi kapalıdır, Steam çalışır. */
class ProviderNotConfiguredTest extends IntegrationTestSupport {

    @Test
    void googleKimlikBilgisiYoksaGirisKapalidir() throws Exception {
        mockMvc.perform(get(AuthApiPaths.GOOGLE_START)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_NOT_CONFIGURED"));
        mockMvc.perform(get(AuthApiPaths.GOOGLE_CALLBACK).param("code", "x").param("state", "y"))
                .andExpect(status().isFound());
    }

    @Test
    void steamAnahtarGerektirmedigindenBaslangicAcikTir() throws Exception {
        mockMvc.perform(get(AuthApiPaths.STEAM_START)).andExpect(status().isFound());
    }
}
