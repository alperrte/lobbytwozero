package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.IntegrationTestSupport;

/**
 * Yaş doğrulaması (18+): beyan, geri alma, deneme sınırı, {@code /me} alanı ve access token iddiası. Doğum tarihi hiçbir
 * yerde saklanmaz; yalnız sonuç (tarih, yöntem) tutulur.
 */
class AccountAgeVerificationTest extends IntegrationTestSupport {

    // ---- Beyan ----

    @Test
    void guvenilirHesapBeyanEderMeVeTokenDogrulanmisGosterir() throws Exception {
        UUID accountId = securedAccount();
        String bearer = bearerFor(accountId);
        me(bearer).andExpect(jsonPath("$.adultVerified").value(false));
        assertThat(claims(bearer)).doesNotContain("adult_verified");

        verify(bearer, adultBirthDate()).andExpect(status().isNoContent());

        me(bearer).andExpect(status().isOk()).andExpect(jsonPath("$.adultVerified").value(true))
                .andExpect(jsonPath("$.birthDate").doesNotExist()).andExpect(jsonPath("$.age").doesNotExist());
        assertThat(claims(bearerFor(accountId))).contains("\"adult_verified\":true");
        assertThat(jdbc.queryForObject("select method from account_age_verifications where account_id = ?",
                String.class, accountId)).isEqualTo("SELF_DECLARED");
        assertThat(securityEventCount(accountId, "AGE_VERIFIED")).isEqualTo(1);
    }

    @Test
    void tekrarBeyanIdempotenttirVeDenemeHakkiHarcamaz() throws Exception {
        UUID accountId = securedAccount();
        String bearer = bearerFor(accountId);

        for (int i = 0; i < 5; i++) {
            verify(bearer, adultBirthDate()).andExpect(status().isNoContent());
        }

        assertThat(jdbc.queryForObject("select count(*) from account_age_verifications where account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
        assertThat(securityEventCount(accountId, "AGE_VERIFIED")).isEqualTo(1);
    }

    @Test
    void onSekizYasAltiReddedilirKayitOlusmazOlayYazilir() throws Exception {
        UUID accountId = securedAccount();

        verify(bearerFor(accountId), underageBirthDate()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AGE_NOT_ELIGIBLE"));

        assertThat(rows(accountId)).isZero();
        assertThat(securityEventCount(accountId, "AGE_VERIFICATION_REJECTED")).isEqualTo(1);
    }

    @Test
    void gelecekVeYuzYirmiYilOncesiTarihGecersizdirDenemeHakkiHarcamaz() throws Exception {
        UUID accountId = securedAccount();
        String bearer = bearerFor(accountId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (int i = 0; i < 5; i++) {
            verify(bearer, today.plusDays(1)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_BIRTH_DATE"));
        }
        verify(bearer, today.minusYears(121)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BIRTH_DATE"));
        verify(bearer, adultBirthDate()).andExpect(status().isNoContent());
    }

    @Test
    void bozukBicimliVeEksikTarih400Doner() throws Exception {
        String bearer = bearerFor(securedAccount());

        postRaw(bearer, "{\"birthDate\":\"31-12-2000\"}").andExpect(status().isBadRequest());
        postRaw(bearer, "{}").andExpect(status().isBadRequest());
    }

    // ---- Deneme sınırı ----

    @Test
    void ucDenemedenSonraDorduncuDenemeKilitlenirBasariSayaciSifirlar() throws Exception {
        UUID accountId = securedAccount();
        String bearer = bearerFor(accountId);

        for (int i = 0; i < 3; i++) {
            verify(bearer, underageBirthDate()).andExpect(status().isForbidden());
        }
        verify(bearer, underageBirthDate()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        // Kilitliyken doğru tarih de reddedilir.
        verify(bearer, adultBirthDate()).andExpect(status().isTooManyRequests());
        assertThat(rows(accountId)).isZero();
    }

    @Test
    void basariliBeyanSayaciSifirlarGeriAlipYenidenDenenebilir() throws Exception {
        UUID accountId = securedAccount();
        String bearer = bearerFor(accountId);

        verify(bearer, underageBirthDate()).andExpect(status().isForbidden());
        verify(bearer, underageBirthDate()).andExpect(status().isForbidden());
        verify(bearer, adultBirthDate()).andExpect(status().isNoContent());
        revoke(bearer).andExpect(status().isNoContent());

        for (int i = 0; i < 3; i++) {
            verify(bearer, underageBirthDate()).andExpect(status().isForbidden());
        }
    }

    // ---- Güvence (K7) ve yetki ----

    @Test
    void guvenilirOlmayanHesapStepUpRequiredAlirKayitOlusmaz() throws Exception {
        UUID accountId = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));

        verify(bearerFor(accountId), adultBirthDate()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));

        assertThat(rows(accountId)).isZero();
    }

    @Test
    void kimliksizIstek401() throws Exception {
        mockMvc.perform(post(AuthApiPaths.ACCOUNT_AGE_VERIFICATION).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(adultBirthDate())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(AuthApiPaths.ACCOUNT_AGE_VERIFICATION).with(xsrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void govdedeBaskaHesapKimligiReddedilirYalnizTokenSahibiDogrulanir() throws Exception {
        UUID owner = securedAccount();
        UUID other = securedAccount();

        postRaw(bearerFor(owner), "{\"birthDate\":\"%s\",\"accountId\":\"%s\"}".formatted(adultBirthDate(), other))
                .andExpect(status().isBadRequest());
        verify(bearerFor(owner), adultBirthDate()).andExpect(status().isNoContent());

        assertThat(rows(owner)).isEqualTo(1);
        assertThat(rows(other)).isZero();
    }

    // ---- Geri alma ----

    @Test
    void geriAlmaIdempotenttirIddiaDuserSatirKalirYenidenBeyanEdilebilir() throws Exception {
        UUID accountId = securedAccount();
        String bearer = bearerFor(accountId);
        revoke(bearer).andExpect(status().isNoContent());
        assertThat(securityEventCount(accountId, "AGE_VERIFICATION_REVOKED")).isZero();

        verify(bearer, adultBirthDate()).andExpect(status().isNoContent());
        revoke(bearer).andExpect(status().isNoContent());
        revoke(bearer).andExpect(status().isNoContent());

        me(bearer).andExpect(jsonPath("$.adultVerified").value(false));
        assertThat(claims(bearerFor(accountId))).doesNotContain("adult_verified");
        assertThat(jdbc.queryForMap("select revoked_by, revoked_at is not null as revoked "
                + "from account_age_verifications where account_id = ?", accountId))
                .containsEntry("revoked_by", "SELF").containsEntry("revoked", true);
        assertThat(securityEventCount(accountId, "AGE_VERIFICATION_REVOKED")).isEqualTo(1);

        verify(bearer, adultBirthDate()).andExpect(status().isNoContent());
        me(bearer).andExpect(jsonPath("$.adultVerified").value(true));
        assertThat(jdbc.queryForObject("select revoked_at is null from account_age_verifications "
                + "where account_id = ?", Boolean.class, accountId)).isTrue();
    }

    @Test
    void yenilemeDogrulamayiTokenaYansitir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        var session = loginSession(email);
        enableTotp(accountId);
        assertThat(claims("Bearer " + session.access().getValue())).doesNotContain("adult_verified");

        verify(bearerFor(accountId), adultBirthDate()).andExpect(status().isNoContent());
        var refreshed = refresh(session.refresh()).andExpect(status().isOk()).andReturn();

        assertThat(claims("Bearer " + refreshed.getResponse().getCookie(ACCESS_COOKIE).getValue()))
                .contains("\"adult_verified\":true");
    }

    // ---- Silme, veri minimizasyonu ----

    @Test
    void hesapSilinincePasifKayitDaGider() throws Exception {
        UUID accountId = securedAccount();
        verify(bearerFor(accountId), adultBirthDate()).andExpect(status().isNoContent());
        assertThat(rows(accountId)).isEqualTo(1);

        jdbc.update("delete from security_events where account_id = ?", accountId);
        jdbc.update("delete from accounts where id = ?", accountId);

        assertThat(rows(accountId)).isZero();
    }

    @Test
    void dogumTarihiSaklanmazYanittaDonmezYalnizSonucTutulur() throws Exception {
        UUID accountId = securedAccount();
        LocalDate birth = adultBirthDate();

        String response = verify(bearerFor(accountId), birth).andExpect(status().isNoContent()).andReturn()
                .getResponse().getContentAsString();

        assertThat(response).doesNotContain(birth.toString());
        assertThat(jdbc.queryForList("select column_name from information_schema.columns "
                + "where table_name = 'account_age_verifications'", String.class))
                .containsExactlyInAnyOrder("account_id", "verified_at", "method", "revoked_at", "revoked_by",
                        "version");
    }

    // ---- Yardımcılar ----

    private UUID securedAccount() throws Exception {
        UUID accountId = accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
        enableTotp(accountId);
        return accountId;
    }

    private int rows(UUID accountId) {
        return jdbc.queryForObject("select count(*) from account_age_verifications where account_id = ?",
                Integer.class, accountId);
    }

    private static LocalDate adultBirthDate() {
        return LocalDate.now(ZoneOffset.UTC).minusYears(30);
    }

    private static LocalDate underageBirthDate() {
        return LocalDate.now(ZoneOffset.UTC).minusYears(17);
    }

    private static String body(LocalDate date) {
        return "{\"birthDate\":\"%s\"}".formatted(date);
    }

    private ResultActions verify(String bearer, LocalDate date) throws Exception {
        return postRaw(bearer, body(date));
    }

    private ResultActions postRaw(String bearer, String json) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.ACCOUNT_AGE_VERIFICATION).with(xsrf())
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions revoke(String bearer) throws Exception {
        return mockMvc.perform(delete(AuthApiPaths.ACCOUNT_AGE_VERIFICATION).with(xsrf())
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions me(String bearer) throws Exception {
        return mockMvc.perform(get(AuthApiPaths.ME).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private static String claims(String bearer) {
        String token = bearer.substring("Bearer ".length());
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }
}
