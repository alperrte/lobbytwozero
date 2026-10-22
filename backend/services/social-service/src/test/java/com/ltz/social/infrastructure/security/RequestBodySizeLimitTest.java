package com.ltz.social.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.api.controller.SocialApiPaths;

class RequestBodySizeLimitTest extends IntegrationTestSupport {

    private static final int OVER_LIMIT_BYTES = 9 * 1024;

    @Test
    void sinirinUstundekiGovde413_problemDetail() throws Exception {
        String oversized = "{\"receiverId\":\"" + "a".repeat(OVER_LIMIT_BYTES) + "\"}";

        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversized))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void normalBoyutluGovdeKabulEdilir() throws Exception {
        sendRequest(newUser(), newUser()).andExpect(status().isCreated());
    }
}
