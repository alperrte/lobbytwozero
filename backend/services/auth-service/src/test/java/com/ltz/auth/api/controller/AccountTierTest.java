package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.AccountDeletionTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.application.service.LoginResult;
import com.ltz.auth.application.service.LoginService;
import com.ltz.auth.domain.enums.AuthMethod;

/**
 * Hesap türleri (A/B/C): {@code /me} alanları, token claim'leri, silme/e-posta/2FA kuralları ve yönetici onaylı silme
 * talebi. Tür saklanmaz; kayıt yönteminden, TOTP'den ve bağlı sağlayıcılardan hesaplanır.
 */
@Import(RecordingMailPortConfiguration.class)
class AccountTierTest extends AccountDeletionTestSupport {

    @Autowired
    private LoginService loginService;

    // ---- Tür hesaplama (/me ve token) ----

    @Test
    void normalKayitliVeGuvencesizHesapATurudurKisitliAlanlarDoner() throws Exception {
        UUID accountId = newPasswordAccount();

        me(bearerFor(accountId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountTier").value("A")).andExpect(jsonPath("$.trusted").value(false))
                .andExpect(jsonPath("$.passwordSet").value(true)).andExpect(jsonPath("$.totpEnabled").value(false))
                .andExpect(jsonPath("$.providers").isEmpty())
                .andExpect(jsonPath("$.registrationMethod").value("PASSWORD"));
    }

    @Test
    void ikiFaktorAcikHesapBTurudur() throws Exception {
        UUID accountId = newPasswordAccount();
        enableTotp(accountId);

        me(bearerFor(accountId)).andExpect(jsonPath("$.accountTier").value("B"))
                .andExpect(jsonPath("$.trusted").value(true)).andExpect(jsonPath("$.totpEnabled").value(true));
    }

    @Test
    void sonradanSaglayiciBaglayanHesapBTurudurKayitYontemiDegismez() throws Exception {
        UUID accountId = newPasswordAccount();
        linkProvider(accountId, "STEAM");

        me(bearerFor(accountId)).andExpect(jsonPath("$.accountTier").value("B"))
                .andExpect(jsonPath("$.providers[0]").value("STEAM"))
                .andExpect(jsonPath("$.registrationMethod").value("PASSWORD"));
    }

    @Test
    void dogrudanGoogleVeyaSteamIleKayitOlanCTurudurParolaBelirlenseDeCKalir() throws Exception {
        UUID google = newPasswordAccount();
        jdbc.update("update accounts set registration_method = 'GOOGLE' where id = ?", google);
        UUID steam = newPasswordAccount();
        jdbc.update("update accounts set registration_method = 'STEAM', password_hash = null where id = ?", steam);

        me(bearerFor(google)).andExpect(jsonPath("$.accountTier").value("C"))
                .andExpect(jsonPath("$.passwordSet").value(true))
                .andExpect(jsonPath("$.registrationMethod").value("GOOGLE"));
        me(bearerFor(steam)).andExpect(jsonPath("$.accountTier").value("C"))
                .andExpect(jsonPath("$.passwordSet").value(false))
                .andExpect(jsonPath("$.registrationMethod").value("STEAM"));
    }

    @Test
    void erisimTokeniTurVeGuvenceClaimlerineSahiptir() throws Exception {
        UUID plain = newPasswordAccount();
        UUID secured = newPasswordAccount();
        enableTotp(secured);

        assertThat(claims(bearerFor(plain))).contains("\"tier\":\"A\"").contains("\"trusted\":false");
        assertThat(claims(bearerFor(secured))).contains("\"tier\":\"B\"").contains("\"trusted\":true");
    }

    @Test
    void meKimliksizIstekte401() throws Exception {
        mockMvc.perform(get(AuthApiPaths.ME)).andExpect(status().isUnauthorized());
    }

    // ---- Silme: B ve C kendi başına silemez, yöneticiye talep açar ----

    @Test
    void saglayiciBagliHesapKendiBasinaSilmeBaslatamaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        linkProvider(accountId, "GOOGLE");

        requestDeletion(bearerFor(accountId), passwordJson()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DELETION_REQUIRES_SUPPORT"));
        assertThat(mailPort.deletionsTo(email)).isEmpty();
    }

    @Test
    void cTuruParolasizHesapSilmeIstegindeEnBastaDestekTalebiYonlendirmesiAlir() throws Exception {
        UUID accountId = newPasswordAccount();
        jdbc.update("update accounts set registration_method = 'GOOGLE', password_hash = null where id = ?", accountId);

        requestDeletion(bearerFor(accountId), "{}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DELETION_REQUIRES_SUPPORT"));
    }

    @Test
    void bTuruDestekTalebiAcarAyniAnaIkinciIstekMevcutTalebiDoner() throws Exception {
        UUID accountId = newSecuredAccount();
        String bearer = bearerFor(accountId);
        supportStatus(bearer).andExpect(status().isNoContent());

        String first = openSupport(accountId, "Hesabımı silmek istiyorum").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        String second = openSupport(accountId, null).andExpect(status().isAccepted()).andReturn().getResponse()
                .getContentAsString();

        assertThat(idOf(second)).isEqualTo(idOf(first));
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_support_requests where account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
        assertThat(securityEventCount(accountId, "DELETION_SUPPORT_REQUESTED")).isEqualTo(1);
        supportStatus(bearer).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").doesNotExist()).andExpect(jsonPath("$.decisionNote").doesNotExist());
    }

    @Test
    void aTuruDestekTalebiAcmazDogrudanKendiSiler() throws Exception {
        UUID accountId = newPasswordAccount();

        supportRequest(bearerFor(accountId), "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_SELF_SERVICE_AVAILABLE"));
        assertThat(supportCount(accountId)).isZero();
    }

    @Test
    void yoneticiDestekTalebiAcamazVeKimliksizIstek401() throws Exception {
        UUID admin = newAdmin();

        supportRequest(bearerFor(admin), "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_DELETION_FORBIDDEN"));
        mockMvc.perform(get(AuthApiPaths.ACCOUNT_DELETION_SUPPORT_REQUEST)).andExpect(status().isUnauthorized());
    }

    @Test
    void yoneticiOnayiStandartSilmeSurecini60BaslatirOturumlarKapanir() throws Exception {
        UUID accountId = newSecuredAccount();
        String requestId = idOf(openSupport(accountId, "silin").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());
        String adminBearer = bearerFor(newAdmin());

        mockMvc.perform(get(AuthApiPaths.ADMIN_DELETION_REQUESTS).header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='%s')].accountId".formatted(requestId))
                        .value(accountId.toString()))
                .andExpect(jsonPath("$[?(@.id=='%s')].message".formatted(requestId)).value("silin"));

        decide(adminBearer, requestId, "approve", "{\"message\":\"onaylandı\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.decisionNote").value("onaylandı"));

        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .isEqualTo("DELETION_REQUESTED");
        assertThat(pendingTasks(accountId)).isPositive();
        assertThat(securityEventCount(accountId, "DELETION_SUPPORT_APPROVED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from auth_sessions where account_id = ? and revoked_at is null",
                Integer.class, accountId)).isZero();
        // Karar bir kez verilir.
        decide(adminBearer, requestId, "approve", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_SUPPORT_ALREADY_DECIDED"));
        decide(adminBearer, requestId, "reject", "{}").andExpect(status().isConflict());
    }

    @Test
    void yoneticiRedHesabiDegistirmezKullaniciYenidenTalepAcabilir() throws Exception {
        UUID accountId = newSecuredAccount();
        String userBearer = bearerFor(accountId);
        String requestId = idOf(openSupport(accountId, null).andReturn().getResponse().getContentAsString());
        String adminBearer = bearerFor(newAdmin());

        decide(adminBearer, requestId, "reject", "{\"message\":\"şimdilik hayır\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .isEqualTo("ACTIVE");
        assertThat(pendingTasks(accountId)).isZero();
        supportStatus(userBearer).andExpect(jsonPath("$.status").value("REJECTED"));
        // Reddedildikten hemen sonra yeni talep beklemeye takılır; süre dolunca açılabilir.
        openSupport(accountId, null).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DELETION_SUPPORT_COOLDOWN"));
        jdbc.update("update account_deletion_support_requests set decided_at = now() - interval '25 hours' "
                + "where id = ?::uuid", requestId);
        String again = idOf(openSupport(accountId, null).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());
        assertThat(again).isNotEqualTo(requestId);
    }

    @Test
    void artikAktifOlmayanHesabinTalebiOnaylanmazTalepBekleyenKalir() throws Exception {
        UUID accountId = newSecuredAccount();
        String requestId = idOf(openSupport(accountId, null).andReturn().getResponse().getContentAsString());
        jdbc.update("update accounts set status = 'BANNED' where id = ?", accountId);

        decide(bearerFor(newAdmin()), requestId, "approve", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_ACTIVE"));
        assertThat(jdbc.queryForObject("select status from account_deletion_support_requests where id = ?::uuid",
                String.class, requestId)).isEqualTo("PENDING");
    }

    @Test
    void bilinmeyenTalepVeYetkisizCagri() throws Exception {
        String adminBearer = bearerFor(newAdmin());
        decide(adminBearer, UUID.randomUUID().toString(), "approve", "{}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELETION_SUPPORT_REQUEST_NOT_FOUND"));

        String playerBearer = bearerFor(newPasswordAccount());
        mockMvc.perform(get(AuthApiPaths.ADMIN_DELETION_REQUESTS).header(HttpHeaders.AUTHORIZATION, playerBearer))
                .andExpect(status().isForbidden());
        decide(playerBearer, UUID.randomUUID().toString(), "approve", "{}").andExpect(status().isForbidden());
    }

    // ---- E-posta: Google ile açılan hesabın adresi sağlayıcıya bağlıdır ----

    @Test
    void googleIleKayitOlanHesapEpostaDegistiremezParolaBelirlemisOlsaBile() throws Exception {
        UUID accountId = newPasswordAccount();
        jdbc.update("update accounts set registration_method = 'GOOGLE' where id = ?", accountId);

        changeEmail(bearerFor(accountId), "{\"newEmail\":\"%s\",\"password\":\"%s\"}".formatted(uniqueEmail(),
                VALID_PASSWORD)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_LOCKED_BY_PROVIDER"));
        assertThat(securityEventCount(accountId, "EMAIL_CHANGE_REJECTED")).isEqualTo(1);
    }

    @Test
    void steamIleKayitOlanEpostasizHesapIlkAdresiEkleyebilir() throws Exception {
        UUID accountId = newPasswordAccount();
        jdbc.update("update accounts set registration_method = 'STEAM', password_hash = null, email = null, "
                + "email_normalized = null, email_verified_at = null where id = ?", accountId);
        enableTotp(accountId);
        String newEmail = uniqueEmail();

        changeEmail(bearerFor(accountId), "{\"newEmail\":\"%s\",\"code\":\"%s\"}".formatted(newEmail,
                currentTotpCode(accountId))).andExpect(status().isAccepted());
        assertThat(mailPort.awaitLatestCode(newEmail, 1)).matches("^[0-9]{6}$");
    }

    // ---- 2FA kapatma ----

    @Test
    void ikiFaktorKapatilincaGuvencesiKalmayanHesapATurunedusar() throws Exception {
        UUID accountId = newPasswordAccount();
        enableTotp(accountId);
        String bearer = bearerFor(accountId);
        me(bearer).andExpect(jsonPath("$.accountTier").value("B"));

        disableTwoFactor(bearer, "{\"password\":\"%s\",\"code\":\"%s\"}".formatted(VALID_PASSWORD,
                currentTotpCode(accountId))).andExpect(status().is2xxSuccessful());

        me(bearer).andExpect(jsonPath("$.accountTier").value("A")).andExpect(jsonPath("$.trusted").value(false))
                .andExpect(jsonPath("$.totpEnabled").value(false));
        // Artık A: kendi başına silme isteyebilir.
        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted());
    }

    @Test
    void ikiFaktorKapatinceSaglayiciBagliHesapBKalir() throws Exception {
        UUID accountId = newPasswordAccount();
        enableTotp(accountId);
        linkProvider(accountId, "GOOGLE");
        String bearer = bearerFor(accountId);

        disableTwoFactor(bearer, "{\"password\":\"%s\",\"code\":\"%s\"}".formatted(VALID_PASSWORD,
                currentTotpCode(accountId))).andExpect(status().is2xxSuccessful());

        me(bearer).andExpect(jsonPath("$.accountTier").value("B"));
    }

    @Test
    void ikiFaktorKapatmakIcinYanlisParolaReddedilir() throws Exception {
        UUID accountId = newPasswordAccount();
        enableTotp(accountId);

        disableTwoFactor(bearerFor(accountId), "{\"password\":\"wrong-password-value\",\"code\":\"%s\"}"
                .formatted(currentTotpCode(accountId))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        disableTwoFactor(bearerFor(accountId), "{\"code\":\"%s\"}".formatted(currentTotpCode(accountId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void parolasizHesapIkiFaktoruYalnizTotpKoduylaKapatirCTuruKalir() throws Exception {
        UUID accountId = newPasswordAccount();
        jdbc.update("update accounts set registration_method = 'STEAM', password_hash = null where id = ?", accountId);
        enableTotp(accountId);
        String bearer = bearerFor(accountId);

        disableTwoFactor(bearer, "{\"code\":\"%s\"}".formatted(currentTotpCode(accountId)))
                .andExpect(status().is2xxSuccessful());

        me(bearer).andExpect(jsonPath("$.accountTier").value("C")).andExpect(jsonPath("$.totpEnabled").value(false));
    }

    @Test
    void zorunluRolHesabiIkiFaktoruKapatamaz() throws Exception {
        UUID accountId = newPasswordAccount();
        enableTotp(accountId);
        grantRole(accountId, "MODERATOR");

        disableTwoFactor(bearerFor(accountId), "{\"password\":\"%s\",\"code\":\"%s\"}".formatted(VALID_PASSWORD,
                currentTotpCode(accountId))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED_FOR_ROLE"));
    }

    @Test
    void steamHesabiArtikIkiFaktorKurulumuZorunluOlmadanOturumAlir() {
        UUID accountId = newPasswordAccountUnchecked();
        jdbc.update("update accounts set registration_method = 'STEAM', password_hash = null where id = ?", accountId);
        linkProvider(accountId, "STEAM");

        LoginResult result = loginService.loginVerifiedIdentity(accountId, AuthMethod.EXTERNAL);

        assertThat(result.kind()).isEqualTo(LoginResult.Kind.AUTHENTICATED);
        assertThat(result.session()).isNotNull();
    }

    // ---- Talep açarken yeniden doğrulama ve geri alınabilirlik ----

    @Test
    void destekTalebiYenidenDogrulamaOlmadanAcilmaz() throws Exception {
        UUID accountId = newSecuredAccount();

        // Yalnız oturum yetmez: parola yok, ikinci faktör yok.
        supportRequest(bearerFor(accountId), "{}").andExpect(status().is4xxClientError());
        supportRequest(bearerFor(accountId), "{\"password\":\"%s\"}".formatted(VALID_PASSWORD))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));
        supportRequest(bearerFor(accountId), "{\"password\":\"wrong-password-value\",\"code\":\"%s\"}"
                .formatted(freshCode(accountId))).andExpect(status().is4xxClientError());
        assertThat(supportCount(accountId)).isZero();

        openSupport(accountId, null).andExpect(status().isAccepted());
        assertThat(supportCount(accountId)).isEqualTo(1);
    }

    @Test
    void parolasizHesapTotpOlmadanDestekTalebiAcamaz() throws Exception {
        UUID accountId = newPasswordAccount();
        jdbc.update("update accounts set registration_method = 'GOOGLE', password_hash = null where id = ?", accountId);

        supportRequest(bearerFor(accountId), "{}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DELETION_REQUIRES_SECOND_FACTOR"));
    }

    @Test
    void parolasizHesapTotpKoduylaTalepAcarVeOnaydanSonraYineKoduylaVazgecer() throws Exception {
        UUID accountId = newPasswordAccount();
        jdbc.update("update accounts set registration_method = 'STEAM', password_hash = null where id = ?", accountId);
        enableTotp(accountId);
        String requestId = idOf(openSupport(accountId, null).andExpect(status().isAccepted()).andReturn().getResponse()
                .getContentAsString());
        decide(bearerFor(newAdmin()), requestId, "approve", null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .isEqualTo("DELETION_REQUESTED");

        jdbc.update("update account_deletion_requests set scheduled_for = now() + interval '14 days' "
                + "where account_id = ?", accountId);
        // Parola yok: yanlış kodla vazgeçilemez, doğru kodla vazgeçilir.
        cancelDeletion(usernameOf(accountId), "{\"code\":\"000000\"}").andExpect(status().isUnauthorized());
        cancelDeletion(usernameOf(accountId), "{\"code\":\"%s\"}".formatted(freshCode(accountId)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void parolasizHesapVazgecmeyiKodsuzDenerseReddedilir() throws Exception {
        UUID accountId = newPasswordAccount();
        jdbc.update("update accounts set registration_method = 'GOOGLE', password_hash = null where id = ?", accountId);

        cancelDeletion(usernameOf(accountId), "{}").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void talepSonrasiGuvenceKalkarsaOnayReddedilirTalepBekler() throws Exception {
        UUID accountId = newSecuredAccount();
        String requestId = idOf(openSupport(accountId, null).andReturn().getResponse().getContentAsString());
        jdbc.update("delete from totp_credentials where account_id = ?", accountId);

        decide(bearerFor(newAdmin()), requestId, "approve", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETION_SELF_SERVICE_AVAILABLE"));
        assertThat(jdbc.queryForObject("select status from accounts where id = ?", String.class, accountId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select status from account_deletion_support_requests where id = ?::uuid",
                String.class, requestId)).isEqualTo("PENDING");
    }

    @Test
    void govdesizIstekKabulEdilirYoneticiKararNotuIsteBaglidir() throws Exception {
        UUID accountId = newSecuredAccount();
        String requestId = idOf(openSupport(accountId, null).andReturn().getResponse().getContentAsString());

        decide(bearerFor(newAdmin()), requestId, "reject", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ---- Yardımcılar ----

    private UUID newPasswordAccount() throws Exception {
        return accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
    }

    private UUID newPasswordAccountUnchecked() {
        try {
            return newPasswordAccount();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** B türü: normal kayıt + 2FA. */
    private UUID newSecuredAccount() throws Exception {
        UUID accountId = newPasswordAccount();
        enableTotp(accountId);
        return accountId;
    }

    private UUID newAdmin() throws Exception {
        UUID admin = newPasswordAccount();
        enableTotp(admin);
        grantRole(admin, "ADMIN");
        return admin;
    }

    private void linkProvider(UUID accountId, String provider) {
        jdbc.update("insert into external_identities (id, account_id, provider, provider_subject, linked_at) "
                + "values (gen_random_uuid(), ?, ?, ?, now())", accountId, provider, "sub-" + uniqueSuffix());
    }

    private int supportCount(UUID accountId) {
        return jdbc.queryForObject("select count(*) from account_deletion_support_requests where account_id = ?",
                Integer.class, accountId);
    }

    private ResultActions me(String bearer) throws Exception {
        return mockMvc.perform(get(AuthApiPaths.ME).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions supportRequest(String bearer, String json) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.ACCOUNT_DELETION_SUPPORT_REQUEST)
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Parola ve (varsa) TOTP kodu ile doğrulanmış destek talebi açar. */
    private ResultActions openSupport(UUID accountId, String message) throws Exception {
        boolean hasPassword = Boolean.TRUE.equals(jdbc.queryForObject(
                "select password_hash is not null from accounts where id = ?", Boolean.class, accountId));
        boolean hasTotp = jdbc.queryForObject("select count(*) from totp_credentials where account_id = ?",
                Integer.class, accountId) > 0;
        StringBuilder json = new StringBuilder("{");
        if (message != null) {
            json.append("\"message\":\"").append(message).append("\",");
        }
        if (hasPassword) {
            json.append("\"password\":\"").append(VALID_PASSWORD).append("\",");
        }
        if (hasTotp) {
            json.append("\"code\":\"").append(freshCode(accountId)).append("\",");
        }
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }
        json.append('}');
        return supportRequest(bearerFor(accountId), json.toString());
    }

    /** Aynı 30 sn'lik adımda tekrar kullanım (replay) engeline takılmamak için son kullanılan adım sıfırlanır. */
    private String freshCode(UUID accountId) {
        jdbc.update("update totp_credentials set last_used_step = null where account_id = ?", accountId);
        return currentTotpCode(accountId);
    }

    private ResultActions cancelDeletion(String identifier, String partialJson) throws Exception {
        String extra = partialJson.length() > 2 ? "," + partialJson.substring(1) : "}";
        return mockMvc.perform(post(AuthApiPaths.ACCOUNT_DELETION_CANCEL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\"" + extra));
    }

    private ResultActions supportStatus(String bearer) throws Exception {
        return mockMvc.perform(get(AuthApiPaths.ACCOUNT_DELETION_SUPPORT_REQUEST)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions decide(String adminBearer, String requestId, String decision, String json) throws Exception {
        var builder = post(AuthApiPaths.ADMIN_DELETION_REQUESTS + "/" + requestId + "/" + decision)
                .header(HttpHeaders.AUTHORIZATION, adminBearer);
        return mockMvc.perform(json == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changeEmail(String bearer, String json) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions disableTwoFactor(String bearer, String json) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.TOTP_DISABLE).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** JWT yükü (imza doğrulaması bu testin konusu değildir; token uygulamanın kendi üreticisinden gelir). */
    private static String claims(String bearer) {
        String token = bearer.substring("Bearer ".length());
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    private static String idOf(String json) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(json);
        assertThat(matcher.find()).as(json).isTrue();
        return matcher.group(1);
    }
}
