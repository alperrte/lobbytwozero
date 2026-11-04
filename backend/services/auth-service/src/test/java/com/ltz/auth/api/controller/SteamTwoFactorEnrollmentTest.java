package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.ltz.auth.ExternalProviderTestSupport;

/**
 * Steam kimliği bağlı hesapta zorunlu 2FA (2026-11-05 kararı): TOTP henüz kurulu değilse giriş oturum vermez, yalnız
 * kurulumu tamamlamaya yarayan kısa ömürlü bir challenge döner; kurulum bitince oturum açılır. Parola ve Steam girişi
 * aynı kuralı izler. Ayar üretimde açık, dev'de kapalıdır; bu sınıf açık haliyle çalışır.
 */
class SteamTwoFactorEnrollmentTest extends ExternalProviderTestSupport {

    private static final String STEAM_ID_PREFIX = "https://steamcommunity.com/openid/id/";

    @DynamicPropertySource
    static void enforceEnrollment(DynamicPropertyRegistry registry) {
        registry.add("ltz.auth.two-factor.require-for-steam-accounts", () -> "true");
    }

    @BeforeEach
    void resetProviders() {
        PROVIDERS.reset();
    }

    @Test
    void steamGirisiTotpYoksaOturumVermezKurulumChallengeiDoner() throws Exception {
        Linked linked = steamLinkedAccount();

        MvcResult result = steamLogin(linked.steamId());

        String fragment = fragment(result);
        assertThat(fragment).startsWith("status=mfa_enrollment_required&challenge=");
        assertThat(result.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE)).isNull();
        assertThat(sessionCount(linked.account())).isZero();
        assertThat(jdbc.queryForObject("select purpose from mfa_challenges where account_id = ?", String.class,
                linked.account())).isEqualTo("ENROLLMENT");
    }

    @Test
    void parolaGirisiDeAyniKurulumKuraliniIzler() throws Exception {
        Linked linked = steamLinkedAccount();

        MvcResult result = login(linked.email(), VALID_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_ENROLLMENT_REQUIRED"))
                .andExpect(jsonPath("$.challengeId").exists()).andReturn();

        assertThat(result.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        assertThat(sessionCount(linked.account())).isZero();
    }

    @Test
    void steamBagliOlmayanHesapEtkilenmez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());

        login(email, VALID_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
    }

    @Test
    void kurulumTamamlaninceTotpEtkinlesirKurtarmaKodlariVeOturumGelir() throws Exception {
        Linked linked = steamLinkedAccount();
        String challenge = enrollmentChallenge(linked);

        MvcResult setup = enrollmentStart(challenge).andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty()).andExpect(jsonPath("$.otpauthUri").isNotEmpty())
                .andReturn();
        assertThat(setup.getResponse().getContentAsString()).contains("otpauth://totp/");
        assertThat(totpStatus(linked.account())).isEqualTo("PENDING");

        MvcResult done = enrollmentComplete(challenge, currentTotpCode(linked.account())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10)).andReturn();

        assertThat(done.getResponse().getCookie(ACCESS_COOKIE)).isNotNull();
        assertThat(done.getResponse().getCookie(REFRESH_COOKIE)).isNotNull();
        assertThat(done.getResponse().getContentAsString()).doesNotContain("\"accessToken\"")
                .doesNotContain("\"refreshToken\"");
        assertThat(totpStatus(linked.account())).isEqualTo("ACTIVE");
        assertThat(sessionCount(linked.account())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select consumed_at is not null from mfa_challenges where account_id = ?",
                Boolean.class, linked.account())).isTrue();

        // Tek kullanımlık: aynı challenge ikinci kez kurulum veya oturum üretemez.
        enrollmentComplete(challenge, currentTotpCode(linked.account())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_FAILED"));
        enrollmentStart(challenge).andExpect(status().isUnauthorized());

        // Artık TOTP etkin: sonraki giriş olağan ikinci faktör adımını ister.
        login(linked.email(), VALID_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
    }

    @Test
    void yanlisKodOturumVermezDenemeSayilirSinirdaKilitlenir() throws Exception {
        Linked linked = steamLinkedAccount();
        String challenge = enrollmentChallenge(linked);
        enrollmentStart(challenge).andExpect(status().isOk());
        String wrong = otherCode(currentTotpCode(linked.account()));

        for (int attempt = 0; attempt < 5; attempt++) {
            enrollmentComplete(challenge, wrong).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));
        }
        // Beşinci yanlıştan sonra doğru kod bile kabul edilmez: challenge tükendi.
        enrollmentComplete(challenge, currentTotpCode(linked.account())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_FAILED"));
        assertThat(totpStatus(linked.account())).isEqualTo("PENDING");
        assertThat(sessionCount(linked.account())).isZero();
    }

    @Test
    void bilinmeyenVeSuresiDolmusChallengeReddedilir() throws Exception {
        enrollmentStart("bilinmeyen-challenge").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_FAILED"));
        enrollmentComplete("bilinmeyen-challenge", "123456").andExpect(status().isUnauthorized());

        Linked linked = steamLinkedAccount();
        String challenge = enrollmentChallenge(linked);
        jdbc.update("update mfa_challenges set expires_at = now() - interval '1 minute', "
                + "created_at = now() - interval '6 minutes' where account_id = ?", linked.account());
        enrollmentStart(challenge).andExpect(status().isUnauthorized());
        enrollmentComplete(challenge, "123456").andExpect(status().isUnauthorized());
    }

    @Test
    void kurulumVeGirisChallengeleriBirbirininYerineGecmez() throws Exception {
        // Kurulum challenge'ı olağan 2FA girişinde geçersizdir.
        Linked linked = steamLinkedAccount();
        String enrollment = enrollmentChallenge(linked);
        enableTotp(linked.account());
        mockMvc.perform(post(AuthApiPaths.LOGIN_TWO_FACTOR).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(enrollment,
                                currentTotpCode(linked.account()))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MFA_FAILED"));
        assertThat(sessionCount(linked.account())).isZero();

        // Giriş (LOGIN) challenge'ı kurulum uçlarında geçersizdir.
        String loginChallenge = login(linked.email(), VALID_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED")).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(loginChallenge, "$.challengeId");
        enrollmentStart(id).andExpect(status().isUnauthorized());
        enrollmentComplete(id, currentTotpCode(linked.account())).andExpect(status().isUnauthorized());
    }

    @Test
    void steamHesabindaTotpKapatilamaz() throws Exception {
        Linked linked = steamLinkedAccount();
        enableTotp(linked.account());

        mockMvc.perform(post(AuthApiPaths.TOTP_DISABLE)
                        .header("Authorization", bearerFor(linked.account()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\",\"code\":\"%s\"}".formatted(VALID_PASSWORD,
                                currentTotpCode(linked.account()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED_FOR_ROLE"));
        assertThat(totpStatus(linked.account())).isEqualTo("ACTIVE");
    }

    @Test
    void steamBagliParolasizHesapTotpOlmadanParolaBelirlemeKoduIsteyemez() throws Exception {
        Linked linked = steamLinkedAccount();
        jdbc.update("update accounts set password_hash = null where id = ?", linked.account());

        mockMvc.perform(post(AuthApiPaths.PASSWORD_SET_REQUEST).header("Authorization", bearerFor(linked.account()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_ENROLLMENT_REQUIRED"));
        assertThat(securityEventCount(linked.account(), "PASSWORD_SET_REJECTED")).isEqualTo(1);
    }

    // ---- Yardımcılar ----

    private record Linked(UUID account, String email, String steamId) {
    }

    private Linked steamLinkedAccount() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        String steamId = "7656119" + String.format("%010d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 10_000_000_000L);
        jdbc.update("insert into external_identities (id, account_id, provider, provider_subject, linked_at) "
                + "values (gen_random_uuid(), ?, 'STEAM', ?, now())", account, steamId);
        return new Linked(account, email, steamId);
    }

    /** Parola girişinden kurulum challenge'ı alır. */
    private String enrollmentChallenge(Linked linked) throws Exception {
        String body = login(linked.email(), VALID_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_ENROLLMENT_REQUIRED")).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.challengeId");
    }

    private ResultActions enrollmentStart(String challenge) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.LOGIN_TWO_FACTOR_ENROLLMENT_START).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"challengeId\":\"%s\"}".formatted(challenge)));
    }

    private ResultActions enrollmentComplete(String challenge, String code) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.LOGIN_TWO_FACTOR_ENROLLMENT_COMPLETE).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challenge, code)));
    }

    private MvcResult steamLogin(String steamId) throws Exception {
        Started started = start(AuthApiPaths.STEAM_START);
        MockHttpServletRequestBuilder request = get(AuthApiPaths.STEAM_CALLBACK);
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("state", new String[]{started.state()});
        params.put("openid.ns", new String[]{"http://specs.openid.net/auth/2.0"});
        params.put("openid.mode", new String[]{"id_res"});
        params.put("openid.op_endpoint", new String[]{PROVIDERS.url("/steam/openid")});
        params.put("openid.claimed_id", new String[]{STEAM_ID_PREFIX + steamId});
        params.put("openid.identity", new String[]{STEAM_ID_PREFIX + steamId});
        params.put("openid.return_to", new String[]{PUBLIC_BASE + AuthApiPaths.STEAM_CALLBACK + "?state=" + started.state()});
        params.put("openid.response_nonce", new String[]{Instant.now().toString().substring(0, 19) + "Z" + uniqueSuffix()});
        params.put("openid.assoc_handle", new String[]{"1234567890"});
        params.put("openid.signed", new String[]{
                "signed,op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle"});
        params.put("openid.sig", new String[]{"c2lnbmF0dXJl"});
        params.forEach(request::param);
        return mockMvc.perform(request.cookie(started.stateCookie())).andExpect(status().isFound()).andReturn();
    }

    private int sessionCount(UUID account) {
        return jdbc.queryForObject("select count(*) from auth_sessions where account_id = ?", Integer.class, account);
    }

    private String totpStatus(UUID account) {
        return jdbc.queryForObject("select status from totp_credentials where account_id = ?", String.class, account);
    }

    /** Verilen koddan farklı, geçerli biçimde (6 hane) bir kod. */
    private static String otherCode(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }
}
