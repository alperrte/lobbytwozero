package com.ltz.recommendation.infrastructure.persistence;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Başlangıç HTTP yüzeyi kapalı kalır; bu test gerçek JWT/iş endpoint'i kabulü değildir. */
@SpringBootTest
@ContextConfiguration(initializers = DisposableDatabaseInitializer.class)
class RecommendationSecuritySurfaceTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousAndForgedIdentityCannotAccessRecommendationOrAnalytics() throws Exception {
        for (String path : new String[] {"/api/v1/recommendation/games", "/api/v1/recommendation/analytics", "/internal/events"}) {
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", "00000000-0000-0000-0000-000000000001")
                    .header("X-Role", "ADMIN").cookie(new Cookie("ltz_access", "untrusted")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void defaultCsrfProtectionRejectsUntrustedMutation() throws Exception {
        mvc.perform(post("/internal/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isForbidden());
    }
}
