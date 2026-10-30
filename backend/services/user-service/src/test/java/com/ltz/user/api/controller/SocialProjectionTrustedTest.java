package com.ltz.user.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/**
 * Social yansıması güvenilir ilan edildiğinde ({@code ltz.user.social.projection-enabled=true}): karar ucu arkadaşlık ve
 * engeli hesaba katar, açık profil arkadaş sayısını gösterir.
 */
@TestPropertySource(properties = "ltz.user.social.projection-enabled=true")
class SocialProjectionTrustedTest extends IntegrationTestSupport {

    @Test
    void kararUcuIliskiyeGoreIzinVerir() throws Exception {
        UUID owner = newUser();
        insertProfile(owner);
        insertPrivacy(owner, "FRIENDS", "PUBLIC", "FRIENDS", "PRIVATE", "BASIC");
        UUID friend = newUser();
        UUID member = newUser();
        befriend(friend, owner);

        decide(friend, "FRIENDS_LIST", owner).andExpect(jsonPath("$.decisions[0].allowed").value(true))
                .andExpect(jsonPath("$.decisions[0].friends").value(true));
        decide(member, "FRIENDS_LIST", owner).andExpect(jsonPath("$.decisions[0].allowed").value(false))
                .andExpect(jsonPath("$.decisions[0].friends").value(false));
        decide(member, "LIBRARY", owner).andExpect(jsonPath("$.decisions[0].allowed").value(true));
        decide(friend, "PERFORMANCE", owner).andExpect(jsonPath("$.decisions[0].allowed").value(false));
        // Misafir: BASIC iken PUBLIC bölüm bile kapalı.
        decide(null, "LIBRARY", owner).andExpect(jsonPath("$.decisions[0].allowed").value(false));
    }

    @Test
    void engelHerIkiYondeIzniKaldirir() throws Exception {
        UUID owner = newUser();
        insertProfile(owner);
        insertPrivacy(owner, "PUBLIC", "PUBLIC", "PUBLIC", "PUBLIC", "DETAILED");
        UUID blockedFriend = newUser();
        befriend(owner, blockedFriend);
        block(owner, blockedFriend);
        UUID blocker = newUser();
        block(blocker, owner);

        decide(blockedFriend, "LIBRARY", owner).andExpect(jsonPath("$.decisions[0].allowed").value(false));
        decide(blocker, "LIBRARY", owner).andExpect(jsonPath("$.decisions[0].allowed").value(false));
        decide(newUser(), "LIBRARY", owner).andExpect(jsonPath("$.decisions[0].allowed").value(true));
        decide(null, "LIBRARY", owner).andExpect(jsonPath("$.decisions[0].allowed").value(true));
    }

    @Test
    void acikProfilArkadasSayisiniGosterir() throws Exception {
        UUID owner = newUser();
        insertNamedProfile(owner, "c" + PublicProfileControllerTest.suffix(), "Ad", "ACTIVE");
        insertPrivacy(owner, "PUBLIC", "PUBLIC", "FRIENDS", "PRIVATE", "BASIC");
        befriend(owner, newUser());
        befriend(newUser(), owner);
        jdbc.update("insert into social_friendships (user_low_id, user_high_id, active, changed_at) values (?, ?, false, now())",
                owner, newUser());

        mockMvc.perform(get(UserApiPaths.PROFILE, owner).header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.friends.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.summary.friends.count").value(2));
    }

    private ResultActions decide(UUID viewer, String field, UUID owner) throws Exception {
        String viewerJson = viewer == null ? "null" : "\"" + viewer + "\"";
        String body = "{\"viewerId\":%s,\"field\":\"%s\",\"ownerIds\":[%s]}".formatted(viewerJson, field,
                List.of(owner).stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",")));
        return mockMvc.perform(post(InternalApiPaths.PRIVACY_DECISIONS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, serviceClaims("svc:hardware-service", "privacy:read").build()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
