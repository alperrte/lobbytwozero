package com.ltz.user.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ltz.user.IntegrationTestSupport;

/**
 * Social engel yansıması güvenilir değilken (varsayılan {@code ltz.user.social.projection-enabled=false}) engeller
 * bilinmez; aday keşfi fail-closed olup hiç aday döndürmez (hata da vermez, nedeni ayırt ettirmez).
 */
class RecommendationCandidateFailClosedTest extends IntegrationTestSupport {

    @BeforeEach
    void clearConsents() {
        jdbc.update("delete from user_consents");
    }

    @Test
    void yansimaGuvenilmezkenUygunAdayBileDonmez() throws Exception {
        UUID viewer = eligible();
        eligible();

        String path = InternalApiPaths.RECOMMENDATION_CANDIDATES.replace("{accountId}", viewer.toString());
        String token = sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, serviceClaims("svc:recommendation-service", "candidates:read")
                .claim("account_id", viewer.toString()).build());
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    private UUID eligible() {
        UUID id = newUser();
        insertNamedProfile(id, "f" + PublicProfileControllerTest.suffix(), "Ad", "ACTIVE");
        jdbc.update("update user_profiles set onboarding_completed_at = now() where user_id = ?", id);
        jdbc.update("""
                insert into user_consents (user_id, recommendation_granted, recommendation_changed_at, updated_at, version)
                values (?, true, now(), now(), 0)""", id);
        return id;
    }
}
