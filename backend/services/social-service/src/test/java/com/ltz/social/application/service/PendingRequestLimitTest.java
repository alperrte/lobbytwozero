package com.ltz.social.application.service;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.api.controller.SocialApiPaths;

/** Toplu istek spam'ine karşı kullanıcı başına bekleyen giden istek sınırı (SECURITY.md §12). */
@TestPropertySource(properties = "ltz.social.friend-requests.max-pending-outgoing=2")
class PendingRequestLimitTest extends IntegrationTestSupport {

    @Test
    void sinirAsilincaYeniIstek429_iptalSonrasiTekrarGonderilebilir() throws Exception {
        UUID sender = newUser();
        UUID firstRequest = sendRequestAndGetId(sender, newUser());
        sendRequest(sender, newUser()).andExpect(status().isCreated());

        sendRequest(sender, newUser())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PENDING_REQUESTS"));

        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, sender, firstRequest).andExpect(status().isOk());
        sendRequest(sender, newUser()).andExpect(status().isCreated());
    }

    @Test
    void sinirKullaniciBasinadir_baskasiEtkilenmez() throws Exception {
        UUID busy = newUser();
        sendRequest(busy, newUser()).andExpect(status().isCreated());
        sendRequest(busy, newUser()).andExpect(status().isCreated());

        sendRequest(newUser(), newUser()).andExpect(status().isCreated());
    }

    @Test
    void gelenIstekler_gonderilenSinirinaSayilmaz() throws Exception {
        UUID popular = newUser();
        for (int i = 0; i < 3; i++) {
            sendRequest(newUser(), popular).andExpect(status().isCreated());
        }

        sendRequest(popular, newUser()).andExpect(status().isCreated());
    }
}
