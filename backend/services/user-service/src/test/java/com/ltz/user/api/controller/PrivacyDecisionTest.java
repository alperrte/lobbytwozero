package com.ltz.user.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/**
 * Servislerin gizlilik kararı ({@code POST /internal/v1/privacy/decisions}). Bu bağlamda Social yansıması güvenilir
 * değildir (varsayılan): sahibi olmayan izleyiciye hiçbir izin verilmez, ayar yine döner. Güvenilir mod
 * {@code SocialProjectionTrustedTest} içindedir.
 */
class PrivacyDecisionTest extends IntegrationTestSupport {

    @Test
    void yansimaGuvenilmezkenAyarDonerAmaIzinVerilmez() throws Exception {
        UUID owner = newUser();
        insertProfile(owner);
        insertPrivacy(owner, "PUBLIC", "PUBLIC", "PUBLIC", "PUBLIC", "DETAILED");
        UUID defaults = newUser();

        decide("social-service", newUser(), "FRIENDS_LIST", List.of(owner, defaults))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions[0].ownerId").value(owner.toString()))
                .andExpect(jsonPath("$.decisions[0].setting").value("PUBLIC"))
                .andExpect(jsonPath("$.decisions[0].allowed").value(false))
                .andExpect(jsonPath("$.decisions[0].friends").value(false))
                .andExpect(jsonPath("$.decisions[1].setting").value("FRIENDS"));
    }

    @Test
    void sahipKendiBolumunuHerZamanGorur() throws Exception {
        UUID owner = newUser();
        insertProfile(owner);
        insertPrivacy(owner, "PRIVATE", "PRIVATE", "PRIVATE", "PRIVATE", "BASIC");

        decide("hardware-service", owner, "HARDWARE", List.of(owner))
                .andExpect(jsonPath("$.decisions[0].allowed").value(true))
                .andExpect(jsonPath("$.decisions[0].setting").value("PRIVATE"));
    }

    @Test
    void alanaGoreAyarVeKapaliHesapPrivate() throws Exception {
        UUID owner = newUser();
        insertProfile(owner);
        insertPrivacy(owner, "FRIENDS", "PUBLIC", "PRIVATE", "FRIENDS", "BASIC");
        UUID banned = newUser();
        insertNamedProfile(banned, "b" + PublicProfileControllerTest.suffix(), "Ad", "BANNED");
        insertPrivacy(banned, "PUBLIC", "PUBLIC", "PUBLIC", "PUBLIC", "DETAILED");

        decide("library-service", null, "LIBRARY", List.of(owner, banned))
                .andExpect(jsonPath("$.decisions[0].setting").value("PUBLIC"))
                .andExpect(jsonPath("$.decisions[1].setting").value("PRIVATE"))
                .andExpect(jsonPath("$.decisions[1].allowed").value(false));
        decide("hardware-service", null, "HARDWARE", List.of(owner)).andExpect(jsonPath("$.decisions[0].setting").value("PRIVATE"));
        decide("hardware-service", null, "PERFORMANCE", List.of(owner)).andExpect(jsonPath("$.decisions[0].setting").value("FRIENDS"));
    }

    @Test
    void yinelenenSahiplerBirKezIstekSirasiylaDoner() throws Exception {
        UUID first = newUser();
        UUID second = newUser();

        decide("social-service", null, "FRIENDS_LIST", List.of(second, first, second))
                .andExpect(jsonPath("$.decisions.length()").value(2))
                .andExpect(jsonPath("$.decisions[0].ownerId").value(second.toString()))
                .andExpect(jsonPath("$.decisions[1].ownerId").value(first.toString()));
    }

    // ------------------------------------------------------------ kimlik ve yetki

    @Test
    void izinliOlmayanServis403KapsamYoksa403() throws Exception {
        decide("community-service", null, "LIBRARY", List.of(newUser())).andExpect(status().isForbidden());
        decide("auth-service", null, "LIBRARY", List.of(newUser())).andExpect(status().isForbidden());
        perform(serviceToken("svc:social-service", "events:ingest"), body(null, "LIBRARY", List.of(newUser())))
                .andExpect(status().isForbidden());
    }

    @Test
    void kullaniciTokeniVeTokensiz401() throws Exception {
        String body = body(null, "LIBRARY", List.of(newUser()));

        perform(accessToken(newUser(), UnaryOperator.identity()), body).andExpect(status().isUnauthorized());
        mockMvc.perform(post(InternalApiPaths.PRIVACY_DECISIONS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dogrulama400() throws Exception {
        List<UUID> tooMany = Collections.nCopies(101, UUID.randomUUID());

        decide("social-service", null, "LIBRARY", tooMany).andExpect(status().isBadRequest());
        decide("social-service", null, "LIBRARY", List.of()).andExpect(status().isBadRequest());
        decide("social-service", null, "EMAIL", List.of(newUser())).andExpect(status().isBadRequest());
        perform(serviceToken("svc:social-service", "privacy:read"), "{\"ownerIds\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void getIleAcilmaz() throws Exception {
        mockMvc.perform(get(InternalApiPaths.PRIVACY_DECISIONS).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + serviceToken("svc:social-service", "privacy:read"))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ yardımcılar

    protected ResultActions decide(String caller, UUID viewer, String field, List<UUID> owners) throws Exception {
        return perform(serviceToken("svc:" + caller, "privacy:read"), body(viewer, field, owners));
    }

    private ResultActions perform(String token, String body) throws Exception {
        return mockMvc.perform(post(InternalApiPaths.PRIVACY_DECISIONS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String serviceToken(String subject, String scope) {
        return sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, serviceClaims(subject, scope).build());
    }

    private static String body(UUID viewer, String field, List<UUID> owners) {
        String ids = owners.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        String viewerJson = viewer == null ? "null" : "\"" + viewer + "\"";
        return "{\"viewerId\":%s,\"field\":\"%s\",\"ownerIds\":[%s]}".formatted(viewerJson, field, ids);
    }
}
