package com.ltz.social.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.application.service.InviteCodeService;
import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

/** SOC-14: davet kodu üretimi, iptali ve kodla arkadaşlık isteği başlatma. */
class InviteCodeControllerTest extends IntegrationTestSupport {

    private static final String CODE_FORMAT = "[0-9A-HJKMNP-TV-Z]{10}";

    @Autowired
    private InviteCodeService inviteCodes;

    // ------------------------------------------------------------ kendi kodunu yönetme

    @Test
    void kodYokken_get404() throws Exception {
        getCode(newUser()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_FOUND"));
    }

    @Test
    void kodUretilir_201_ikinciCagriAyniKodu200Doner() throws Exception {
        UUID owner = newUser();

        String created = createCode(owner).andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxUses").value(50))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(created, "$.code");
        assertThat(code).matches(CODE_FORMAT);

        createCode(owner).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
        getCode(owner).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void herKullanicininKendiKoduVardir_baskasininKodunuGoremez() throws Exception {
        UUID first = newUser();
        UUID second = newUser();
        String firstCode = codeOf(first);
        String secondCode = codeOf(second);

        assertThat(firstCode).isNotEqualTo(secondCode);
        getCode(first).andExpect(jsonPath("$.code").value(firstCode));
        getCode(second).andExpect(jsonPath("$.code").value(secondCode));
    }

    @Test
    void kodIptalEdilir_204_idempotent_sonraYeniKodUretilir() throws Exception {
        UUID owner = newUser();
        String oldCode = codeOf(owner);

        revokeCode(owner).andExpect(status().isNoContent());
        getCode(owner).andExpect(status().isNotFound());
        revokeCode(owner).andExpect(status().isNoContent());

        String newCode = JsonPath.read(createCode(owner).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.code");
        assertThat(newCode).isNotEqualTo(oldCode);
        assertThat(revokedAt(oldCode)).isNotNull();
    }

    @Test
    void suresiDolanKodKullanilamaz_yeniKodUretilir() throws Exception {
        UUID owner = newUser();
        String oldCode = codeOf(owner);
        expire(oldCode);

        getCode(owner).andExpect(status().isNotFound());
        String newCode = JsonPath.read(createCode(owner).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.code");

        assertThat(newCode).isNotEqualTo(oldCode);
        assertThat(revokedAt(oldCode)).isNotNull();
        assertThat(revokedAt(newCode)).isNull();
    }

    @Test
    void kullanimHakkiBitenKodYerineYeniKodUretilir() throws Exception {
        UUID owner = newUser();
        String oldCode = codeOf(owner);
        jdbc.update("update friend_invite_codes set used_count = max_uses where code = ?", oldCode);

        String newCode = JsonPath.read(createCode(owner).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.code");

        assertThat(newCode).isNotEqualTo(oldCode);
    }

    @Test
    void eszamanliKodUretimi_tekEtkinKodKalir() throws Exception {
        UUID owner = newUser();

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(() -> inviteCodes.getOrCreate(owner).code().code());
            }
            List<String> codes = new ArrayList<>();
            for (Future<String> future : pool.invokeAll(calls)) {
                codes.add(future.get());
            }
            assertThat(codes).doesNotContainNull().hasSize(6).containsOnly(codes.getFirst());
        } finally {
            pool.shutdownNow();
        }
        Integer active = jdbc.queryForObject(
                "select count(*) from friend_invite_codes where user_id = ? and revoked_at is null", Integer.class,
                owner);
        assertThat(active).isEqualTo(1);
    }

    // ------------------------------------------------------------ kodla istek başlatma

    @Test
    void kodlaIstekGonderilir_201_gonderenTokendenAliciKodSahibidir() throws Exception {
        UUID owner = newUser();
        UUID redeemer = newUser();
        String code = codeOf(owner);

        String body = redeem(redeemer, code).andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(redeemer.toString()))
                .andExpect(jsonPath("$.receiverId").value(owner.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        assertThat(usedCount(code)).isEqualTo(1);
        assertThat(eventTypes(UUID.fromString(JsonPath.read(body, "$.id")))).containsExactly("FriendRequestSent");
        // İstek normal akışla yanıtlanabilir.
        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, owner, UUID.fromString(JsonPath.read(body, "$.id")))
                .andExpect(status().isOk());
        assertThat(areFriends(owner, redeemer)).isTrue();
    }

    @Test
    void elleYazilmisKodNormallestirilir_kucukHarfTireBosluk() throws Exception {
        UUID owner = newUser();
        String code = codeOf(owner);
        String typed = (code.substring(0, 5) + "-" + code.substring(5)).toLowerCase();

        redeem(newUser(), typed).andExpect(status().isCreated());
        redeem(newUser(), " " + code.substring(0, 3) + " " + code.substring(3) + " ").andExpect(status().isCreated());

        assertThat(usedCount(code)).isEqualTo(2);
    }

    @Test
    void kendiKodunuGirenKullanici400_sayacArtmaz() throws Exception {
        UUID owner = newUser();
        String code = codeOf(owner);

        redeem(owner, code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REQUEST_SELF"));

        assertThat(usedCount(code)).isZero();
    }

    @Test
    void bilinmeyenBiçimsizIptalSuresiDolanVeBitenKodlarAyniYaniti404Verir() throws Exception {
        UUID redeemer = newUser();
        String revoked = codeOf(newUser());
        revokeByCode(revoked);
        String expired = codeOf(newUser());
        expire(expired);
        String exhausted = codeOf(newUser());
        jdbc.update("update friend_invite_codes set used_count = max_uses where code = ?", exhausted);

        for (String code : List.of("0123456789", "abc", "!!!###$$$%%%", revoked, expired, exhausted)) {
            redeem(redeemer, code).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_FOUND"));
        }
    }

    @Test
    void bosVeCokUzunKod400() throws Exception {
        UUID redeemer = newUser();

        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS_BY_CODE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(redeemer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        redeem(redeemer, "A".repeat(33)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void zatenArkadasOlunanKisininKoduIle409_sayacArtmaz() throws Exception {
        UUID owner = newUser();
        UUID redeemer = newUser();
        befriend(owner, redeemer);
        String code = codeOf(owner);

        redeem(redeemer, code).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));

        assertThat(usedCount(code)).isZero();
    }

    @Test
    void bekleyenIstekVarkenKodlaTekrarIstek409_sayacYalnizBasarilidaArtar() throws Exception {
        UUID owner = newUser();
        UUID redeemer = newUser();
        String code = codeOf(owner);
        redeem(redeemer, code).andExpect(status().isCreated());

        redeem(redeemer, code).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_ALREADY_PENDING"));

        assertThat(usedCount(code)).isEqualTo(1);
    }

    @Test
    void karsiYondeBekleyenIstekVarsaKodlaIstek409() throws Exception {
        UUID owner = newUser();
        UUID redeemer = newUser();
        sendRequest(owner, redeemer).andExpect(status().isCreated());
        String code = codeOf(owner);

        redeem(redeemer, code).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCOMING_FRIEND_REQUEST_PENDING"));

        assertThat(usedCount(code)).isZero();
    }

    @Test
    void kullanimSiniriDolunca404() throws Exception {
        UUID owner = newUser();
        String code = codeOf(owner);
        jdbc.update("update friend_invite_codes set max_uses = 2 where code = ?", code);

        redeem(newUser(), code).andExpect(status().isCreated());
        redeem(newUser(), code).andExpect(status().isCreated());
        redeem(newUser(), code).andExpect(status().isNotFound());

        assertThat(usedCount(code)).isEqualTo(2);
    }

    @Test
    void eszamanliKullanim_sinirAsilmaz() throws Exception {
        UUID owner = newUser();
        String code = codeOf(owner);
        jdbc.update("update friend_invite_codes set max_uses = 1 where code = ?", code);

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                UUID redeemer = newUser();
                calls.add(() -> {
                    try {
                        inviteCodes.redeem(redeemer, code);
                        return "OK";
                    } catch (SocialException exception) {
                        return exception.errorCode().name();
                    }
                });
            }
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : pool.invokeAll(calls)) {
                outcomes.add(future.get());
            }
            assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
            assertThat(outcomes).filteredOn(result -> !"OK".equals(result))
                    .containsOnly(SocialErrorCode.INVITE_CODE_NOT_FOUND.name());
        } finally {
            pool.shutdownNow();
        }
        assertThat(usedCount(code)).isEqualTo(1);
    }

    @Test
    void kimliksizIstekler401() throws Exception {
        mockMvc.perform(get(SocialApiPaths.INVITE_CODE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(SocialApiPaths.INVITE_CODE)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(SocialApiPaths.INVITE_CODE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS_BY_CODE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"0123456789\"}")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ yardımcılar

    private ResultActions getCode(UUID user) throws Exception {
        return mockMvc.perform(get(SocialApiPaths.INVITE_CODE).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions createCode(UUID user) throws Exception {
        return mockMvc.perform(post(SocialApiPaths.INVITE_CODE).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions revokeCode(UUID user) throws Exception {
        return mockMvc.perform(delete(SocialApiPaths.INVITE_CODE).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions redeem(UUID user, String code) throws Exception {
        return mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS_BY_CODE)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code)));
    }

    private String codeOf(UUID owner) throws Exception {
        return JsonPath.read(createCode(owner).andReturn().getResponse().getContentAsString(), "$.code");
    }

    private int usedCount(String code) {
        return jdbc.queryForObject("select used_count from friend_invite_codes where code = ?", Integer.class, code);
    }

    private Object revokedAt(String code) {
        return jdbc.queryForObject("select revoked_at from friend_invite_codes where code = ?", Object.class, code);
    }

    private void revokeByCode(String code) {
        jdbc.update("update friend_invite_codes set revoked_at = now() where code = ?", code);
    }

    /** Süreyi geçmişe taşır (created_at < expires_at kısıtı korunur). */
    private void expire(String code) {
        jdbc.update("update friend_invite_codes set created_at = now() - interval '2 days', "
                + "expires_at = now() - interval '1 day' where code = ?", code);
    }
}
