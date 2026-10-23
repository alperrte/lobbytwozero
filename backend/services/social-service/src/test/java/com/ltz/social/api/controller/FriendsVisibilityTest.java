package com.ltz.social.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.application.port.FriendsVisibility;
import com.ltz.social.application.port.FriendsVisibilityPort;
import com.ltz.social.application.port.PrivacyUnavailableException;

/** SOC-12: başkasının arkadaş listesi, user-service'teki görünürlük tercihine ve engellere göre sunulur. */
@TestPropertySource(properties = "ltz.social.privacy.adapter=scripted")
@Import(FriendsVisibilityTest.ScriptedVisibilityConfiguration.class)
class FriendsVisibilityTest extends IntegrationTestSupport {

    @Autowired
    private ScriptedVisibility visibility;

    private UUID owner;
    private UUID ownersFriend;

    @BeforeEach
    void setUp() throws Exception {
        visibility.reset();
        owner = newUser();
        ownersFriend = newUser();
        befriend(owner, ownersFriend);
    }

    @Test
    void kullaniciKendiListesiniHerZamanGorur_tercihSorulmaz() throws Exception {
        visibility.set(owner, FriendsVisibility.PRIVATE);

        viewOf(owner, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].friendId").value(ownersFriend.toString()));
        // Kendi listesini görmek için PRIVATE tercih bile engel değildir; tercih sorgusu yapılmaz.
        visibility.unavailable(owner);
        viewOf(owner, owner).andExpect(status().isOk());
    }

    @Test
    void PUBLIC_girisYapmisHerkesGorur() throws Exception {
        visibility.set(owner, FriendsVisibility.PUBLIC);

        viewOf(newUser(), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].friendId").value(ownersFriend.toString()))
                .andExpect(jsonPath("$.items[0].since").isNotEmpty());
    }

    @Test
    void FRIENDS_yalnizArkadaslariGorur() throws Exception {
        visibility.set(owner, FriendsVisibility.FRIENDS);

        viewOf(ownersFriend, owner).andExpect(status().isOk());
        viewOf(newUser(), owner).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FRIENDS_LIST_NOT_VISIBLE"));
    }

    @Test
    void FRIENDS_arkadaslikBitinceGormeHakkiDaBiter() throws Exception {
        visibility.set(owner, FriendsVisibility.FRIENDS);
        viewOf(ownersFriend, owner).andExpect(status().isOk());

        mockMvc.perform(delete(SocialApiPaths.FRIEND, ownersFriend)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        viewOf(ownersFriend, owner).andExpect(status().isForbidden());
    }

    @Test
    void PRIVATE_sahibindenBaskasiGoremez_arkadasDa() throws Exception {
        visibility.set(owner, FriendsVisibility.PRIVATE);

        viewOf(ownersFriend, owner).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FRIENDS_LIST_NOT_VISIBLE"));
        viewOf(newUser(), owner).andExpect(status().isForbidden());
    }

    @Test
    void tercihOkunamazsaFailClosed_gizliKalir() throws Exception {
        // Tercih tanımlı değil: port PrivacyUnavailableException fırlatır → PRIVATE sayılır.
        viewOf(newUser(), owner).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FRIENDS_LIST_NOT_VISIBLE"));
        viewOf(ownersFriend, owner).andExpect(status().isForbidden());
    }

    @Test
    void engelVarsaHerTercihteIkiYondeGorulmez_PUBLIC_dahil() throws Exception {
        visibility.set(owner, FriendsVisibility.PUBLIC);
        UUID blockedByOwner = newUser();
        UUID blockerOfOwner = newUser();
        block(owner, blockedByOwner).andExpect(status().isCreated());
        block(blockerOfOwner, owner).andExpect(status().isCreated());
        visibility.set(blockedByOwner, FriendsVisibility.PUBLIC);
        visibility.set(blockerOfOwner, FriendsVisibility.PUBLIC);

        // Engellenen, engelleyenin listesini göremez.
        viewOf(blockedByOwner, owner).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FRIENDS_LIST_NOT_VISIBLE"));
        // Engelleyen de engellediği kişinin listesini görmez; ters yönde de geçerli.
        viewOf(owner, blockedByOwner).andExpect(status().isForbidden());
        viewOf(owner, blockerOfOwner).andExpect(status().isForbidden());
        viewOf(blockerOfOwner, owner).andExpect(status().isForbidden());
        // Engelle ilgisiz biri etkilenmez.
        viewOf(newUser(), owner).andExpect(status().isOk());
    }

    @Test
    void reddedilenHerDurumdaAyniYaniti_veriyor_engelGizlilikVeVarolmayanKullaniciAyirtEdilemez() throws Exception {
        UUID privateOwner = newUser();
        visibility.set(privateOwner, FriendsVisibility.PRIVATE);
        UUID viewer = newUser();
        UUID blockedOwner = newUser();
        visibility.set(blockedOwner, FriendsVisibility.PUBLIC);
        block(blockedOwner, viewer).andExpect(status().isCreated());

        String privateBody = viewOf(viewer, privateOwner).andExpect(status().isForbidden()).andReturn().getResponse()
                .getContentAsString();
        String blockedBody = viewOf(viewer, blockedOwner).andExpect(status().isForbidden()).andReturn().getResponse()
                .getContentAsString();
        String unknownBody = viewOf(viewer, UUID.randomUUID()).andExpect(status().isForbidden()).andReturn()
                .getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(privateBody, "$.code"))
                .isEqualTo(JsonPath.<String>read(blockedBody, "$.code"))
                .isEqualTo(JsonPath.<String>read(unknownBody, "$.code"));
        assertThat(JsonPath.<String>read(privateBody, "$.detail"))
                .isEqualTo(JsonPath.<String>read(blockedBody, "$.detail"))
                .isEqualTo(JsonPath.<String>read(unknownBody, "$.detail"));
    }

    @Test
    void gorunenListeSayfalanir_vesinirlarUygulanir() throws Exception {
        visibility.set(owner, FriendsVisibility.PUBLIC);
        UUID second = newUser();
        UUID third = newUser();
        befriend(owner, second);
        befriend(owner, third);
        UUID viewer = newUser();

        String firstPage = viewOf(viewer, owner, "?limit=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].friendId").value(third.toString()))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(firstPage, "$.nextCursor");
        viewOf(viewer, owner, "?limit=2&cursor=" + cursor).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].friendId").value(ownersFriend.toString()));
        viewOf(viewer, owner, "?limit=0").andExpect(status().isBadRequest());
    }

    @Test
    void kimliksizIstek401_gecersizKimlik400() throws Exception {
        mockMvc.perform(get(SocialApiPaths.USER_FRIENDS, owner)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/social/users/not-a-uuid/friends")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private ResultActions viewOf(UUID viewer, UUID target) throws Exception {
        return viewOf(viewer, target, "");
    }

    private ResultActions viewOf(UUID viewer, UUID target, String query) throws Exception {
        return mockMvc.perform(get(SocialApiPaths.USER_FRIENDS + query, target)
                .header(HttpHeaders.AUTHORIZATION, bearer(viewer)));
    }

    /** user-service'in görünürlük tercihini taklit eder; tanımsız kullanıcı için "ulaşılamıyor" döner. */
    static class ScriptedVisibility implements FriendsVisibilityPort {

        private final Map<UUID, FriendsVisibility> preferences = new ConcurrentHashMap<>();

        @Override
        public FriendsVisibility visibilityOf(UUID ownerId) {
            FriendsVisibility visibility = preferences.get(ownerId);
            if (visibility == null) {
                throw new PrivacyUnavailableException("scripted: no preference");
            }
            return visibility;
        }

        void set(UUID ownerId, FriendsVisibility visibility) {
            preferences.put(ownerId, visibility);
        }

        void unavailable(UUID ownerId) {
            preferences.remove(ownerId);
        }

        void reset() {
            preferences.clear();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedVisibilityConfiguration {

        @Bean
        ScriptedVisibility scriptedVisibility() {
            return new ScriptedVisibility();
        }
    }
}
