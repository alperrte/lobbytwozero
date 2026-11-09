package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.user.IntegrationTestSupport;

/**
 * Rızalarım (LIB-14, {@code /me/consents}) ve bu rızaya bağlı {@code RECOMMENDATION} gizlilik kararı: varsayılan izin
 * yok, sürüm kuralı, geri çekme, sahiplik kısayolunun uygulanmaması, profili olmayan/kapalı hesap.
 */
class ConsentsTest extends IntegrationTestSupport {

    private static final String CONSENTS = UserApiPaths.ME_CONSENTS;

    // ------------------------------------------------------------ rıza ucu

    @Test
    void okumaVarsayilanRizaYokDonerVeSatirOlusturmaz() throws Exception {
        UUID user = newUser();

        mockMvc.perform(get(CONSENTS).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recommendation").value(false))
                .andExpect(jsonPath("$.recommendationChangedAt").doesNotExist())
                .andExpect(jsonPath("$.version").value(0));

        assertThat(rows(user)).isZero();
    }

    @Test
    void ilkPutProfiliAcarSurumArtarDegisiklikYoksaArtmaz() throws Exception {
        UUID user = newUser();

        putConsent(user, 0, true).andExpect(status().isOk()).andExpect(jsonPath("$.recommendation").value(true))
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.recommendationChangedAt").exists());
        assertThat(profileCount(user)).isEqualTo(1);

        putConsent(user, 0, true).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        putConsent(user, 0, false).andExpect(status().isOk()).andExpect(jsonPath("$.recommendation").value(false))
                .andExpect(jsonPath("$.version").value(1));
        putConsent(user, 0, true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        assertThat(rows(user)).isEqualTo(1);
    }

    @Test
    void ilkKayitIcinSifirDisiSurum409() throws Exception {
        putConsent(newUser(), 3, true).andExpect(status().isConflict());
    }

    @Test
    void eksikAlanTanimsizAlanVeNegatifSurum400() throws Exception {
        UUID user = newUser();

        send(user, "{\"version\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(user, "{\"recommendation\":true}").andExpect(status().isBadRequest());
        send(user, "{\"version\":-1,\"recommendation\":true}").andExpect(status().isBadRequest());
        send(user, "{\"version\":0,\"recommendation\":true,\"x\":1}").andExpect(status().isBadRequest());
        assertThat(rows(user)).isZero();
    }

    @Test
    void tokenYokVeServisTokeni401() throws Exception {
        mockMvc.perform(get(CONSENTS)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(CONSENTS).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"recommendation\":true}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(CONSENTS).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken("svc:library-service", "privacy:read")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void baskasininRizasiniOkuyupYazamaz() throws Exception {
        UUID owner = newUser();
        UUID other = newUser();
        putConsent(owner, 0, true).andExpect(status().isOk());

        mockMvc.perform(get(CONSENTS).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.recommendation").value(false)).andExpect(jsonPath("$.version").value(0));
    }

    // ------------------------------------------------------------ RECOMMENDATION kararı

    @Test
    void rizaYokkenSahipIcinDeIzinYok() throws Exception {
        UUID owner = newUser();
        insertProfile(owner);

        decide(owner, List.of(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions[0].ownerId").value(owner.toString()))
                .andExpect(jsonPath("$.decisions[0].allowed").value(false))
                .andExpect(jsonPath("$.decisions[0].setting").value("PRIVATE"))
                .andExpect(jsonPath("$.decisions[0].friends").value(false));
    }

    @Test
    void rizaVerilinceSahipeIzinGeriCekinceKapanir() throws Exception {
        UUID owner = newUser();
        insertProfile(owner);

        putConsent(owner, 0, true).andExpect(status().isOk());
        decide(owner, List.of(owner)).andExpect(jsonPath("$.decisions[0].allowed").value(true))
                .andExpect(jsonPath("$.decisions[0].setting").value("PUBLIC"));

        putConsent(owner, 0, false).andExpect(status().isOk());
        decide(owner, List.of(owner)).andExpect(jsonPath("$.decisions[0].allowed").value(false));
    }

    @Test
    void rizaGorunurlukAyarindanBagimsiz() throws Exception {
        UUID owner = newUser();
        insertProfile(owner);
        insertPrivacy(owner, "PRIVATE", "PRIVATE", "PRIVATE", "PRIVATE", "BASIC");
        putConsent(owner, 0, true).andExpect(status().isOk());

        decide(owner, List.of(owner)).andExpect(jsonPath("$.decisions[0].allowed").value(true));
    }

    @Test
    void baskaIzleyiciVeMisafirHerZamanReddedilir() throws Exception {
        UUID owner = newUser();
        insertProfile(owner);
        putConsent(owner, 0, true).andExpect(status().isOk());

        decide(newUser(), List.of(owner)).andExpect(jsonPath("$.decisions[0].allowed").value(false));
        decide(null, List.of(owner)).andExpect(jsonPath("$.decisions[0].allowed").value(false));
    }

    @Test
    void profiliOlmayanVeKapaliHesapRizaVerseDeReddedilir() throws Exception {
        UUID noProfile = newUser();
        UUID banned = newUser();
        insertNamedProfile(banned, "c" + PublicProfileControllerTest.suffix(), "Ad", "BANNED");
        jdbc.update("insert into user_consents (user_id, recommendation_granted, recommendation_changed_at, updated_at, version)"
                + " values (?, true, now(), now(), 0)", banned);

        decide(noProfile, List.of(noProfile)).andExpect(jsonPath("$.decisions[0].allowed").value(false));
        decide(banned, List.of(banned)).andExpect(jsonPath("$.decisions[0].allowed").value(false));
    }

    @Test
    void topluSorguHerSahipIcinAyriKararVerir() throws Exception {
        UUID granted = newUser();
        UUID denied = newUser();
        insertProfile(granted);
        insertProfile(denied);
        putConsent(granted, 0, true).andExpect(status().isOk());

        decide(granted, List.of(granted, denied))
                .andExpect(jsonPath("$.decisions[0].allowed").value(true))
                .andExpect(jsonPath("$.decisions[1].allowed").value(false));
    }

    @Test
    void profiliOlmayanSahipteDigerAlanlardaAyarVarsayilanIzinYok() throws Exception {
        UUID owner = newUser();

        mockMvc.perform(post(InternalApiPaths.PRIVACY_DECISIONS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken("svc:library-service", "privacy:read"))
                .contentType(MediaType.APPLICATION_JSON).content(body(newUser(), "LIBRARY", List.of(owner))))
                .andExpect(jsonPath("$.decisions[0].setting").value("PUBLIC"))
                .andExpect(jsonPath("$.decisions[0].allowed").value(false));
    }

    // ------------------------------------------------------------ yardımcılar

    private ResultActions putConsent(UUID user, long version, boolean recommendation) throws Exception {
        return send(user, "{\"version\":%d,\"recommendation\":%s}".formatted(version, recommendation));
    }

    private ResultActions send(UUID user, String body) throws Exception {
        return mockMvc.perform(put(CONSENTS).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions decide(UUID viewer, List<UUID> owners) throws Exception {
        return mockMvc.perform(post(InternalApiPaths.PRIVACY_DECISIONS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken("svc:library-service", "privacy:read"))
                .contentType(MediaType.APPLICATION_JSON).content(body(viewer, "RECOMMENDATION", owners)));
    }

    private int rows(UUID user) {
        return jdbc.queryForObject("select count(*) from user_consents where user_id = ?", Integer.class, user);
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
