package com.ltz.social.api.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.social.IntegrationTestSupport;

/**
 * Varsayılan (gerçek) yapılandırma: user-service adaptörü yok. Başkasının arkadaş listesi fail-closed gizli kalır,
 * kendi listesi etkilenmez. Gerçek adaptör eklenene kadar üretimdeki davranış budur.
 */
class FriendsVisibilityDefaultAdapterTest extends IntegrationTestSupport {

    @Test
    void adaptorYokken_baskasininListesiGorunmez_kendiListesiGorunur() throws Exception {
        UUID owner = newUser();
        UUID friend = newUser();
        befriend(owner, friend);

        list(SocialApiPaths.FRIENDS, owner, "").andExpect(jsonPath("$.items[0].friendId").value(friend.toString()));
        mockMvcView(newUser(), owner).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FRIENDS_LIST_NOT_VISIBLE"));
        mockMvcView(friend, owner).andExpect(status().isForbidden());
        mockMvcView(owner, owner).andExpect(status().isOk());
    }

    private ResultActions mockMvcView(UUID viewer, UUID target) throws Exception {
        return list(SocialApiPaths.USER_FRIENDS.replace("{userId}", target.toString()), viewer, "");
    }
}
