package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.ExternalProviderTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;
import com.nimbusds.jwt.SignedJWT;

@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SteamLoginTest extends ExternalProviderTestSupport {

    private static final String STEAM_ID_PREFIX = "https://steamcommunity.com/openid/id/";

    @Autowired
    private RecordingMailPort mailPort;

    @BeforeEach
    void resetProviders() {
        PROVIDERS.reset();
    }

    @Test
    void baslangicSteamaDogruAlanlarlaYonlendirir() throws Exception {
        Started started = start(AuthApiPaths.STEAM_START);

        Map<String, String> query = query(started.location());
        assertThat(started.location().toString()).startsWith(PROVIDERS.url("/steam/openid"));
        assertThat(query).containsEntry("openid.mode", "checkid_setup").containsEntry("openid.realm", PUBLIC_BASE)
                .containsEntry("openid.return_to", PUBLIC_BASE + AuthApiPaths.STEAM_CALLBACK + "?state=" + started.state())
                .containsEntry("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select");
        assertThat(started.stateCookie().isHttpOnly()).isTrue();
    }

    @Test
    void yeniSteamHesabiEpostaDogrulamasindanSonraGirisYapabilir() throws Exception {
        String steamId = newSteamId();
        String email = uniqueEmail();
        String username = uniqueUsername();

        MvcResult first = steamCallback(steamId, p -> { });
        String registrationToken = fragmentValue(first, "token");
        assertThat(fragment(first)).startsWith("status=registration_required&token=");
        assertThat(first.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        assertThat(PROVIDERS.steamVerifications).singleElement().satisfies(sent -> {
            assertThat(sent).containsEntry("openid.mode", "check_authentication").containsKey("openid.sig")
                    .containsEntry("openid.claimed_id", STEAM_ID_PREFIX + steamId);
        });

        String completeProof = complete(registrationToken, email, username).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nextStep").value("VERIFY_EMAIL"))
                .andExpect(jsonPath("$.verificationProof").isNotEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(completeProof).doesNotContain(registrationToken);

        UUID accountId = accountId(email);
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");
        assertThat(jdbc.queryForObject("select password_hash is null from accounts where id = ?", Boolean.class, accountId))
                .isTrue();
        assertThat(jdbc.queryForObject("select provider_subject from external_identities where account_id = ? "
                + "and provider = 'STEAM'", String.class, accountId)).isEqualTo(steamId);
        MvcResult pendingLogin = steamCallback(steamId, p -> { });
        assertThat(fragment(pendingLogin)).as("e-posta doğrulanmadan oturum yok")
                .startsWith("status=email_verification_required&proof=");
        assertThat(pendingLogin.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        String proof = fragmentValue(pendingLogin, "proof");

        confirmWithProof(email, mailPort.awaitLatestCode(email, 1), proof).andExpect(status().isNoContent());
        MvcResult login = steamCallback(steamId, p -> { });

        assertThat(fragment(login)).isEqualTo("status=authenticated");
        String access = login.getResponse().getCookie(ACCESS_COOKIE).getValue();
        assertThat(SignedJWT.parse(access).getJWTClaimsSet().getStringListClaim("amr")).containsExactly("fed");
        assertThat(accountCount(email)).isEqualTo(1);
    }

    @Test
    void ayniNonceIkinciKezKabulEdilmez() throws Exception {
        String nonce = Instant.now().toString().substring(0, 19) + "Z-replayed-" + uniqueSuffix();

        assertThat(fragment(steamCallback(newSteamId(), p -> p.put("openid.response_nonce", new String[]{nonce}))))
                .startsWith("status=registration_required");

        assertThat(fragment(steamCallback(newSteamId(), p -> p.put("openid.response_nonce", new String[]{nonce}))))
                .isEqualTo("error=EXTERNAL_AUTH_FAILED");
    }

    @Test
    void steamImzayiGecersizSayarsaGirisReddedilir() throws Exception {
        PROVIDERS.steamValid = false;

        MvcResult result = steamCallback(newSteamId(), p -> { });

        assertThat(fragment(result)).isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from steam_registrations", Integer.class)).isZero();
    }

    @Test
    void sahteYanitlarSteamaSorulmadanReddedilir() throws Exception {
        record Case(String name, Consumer<Map<String, String[]>> mutation) {
        }
        List<Case> cases = List.of(
                new Case("baska return_to", p -> p.put("openid.return_to", new String[]{"http://evil.test/cb"})),
                new Case("steam disi claimed_id", p -> {
                    p.put("openid.claimed_id", new String[]{"https://evil.test/openid/id/" + newSteamId()});
                    p.put("openid.identity", new String[]{"https://evil.test/openid/id/" + newSteamId()});
                }),
                new Case("yanlis op_endpoint", p -> p.put("openid.op_endpoint", new String[]{"https://evil.test/openid"})),
                new Case("eski nonce", p -> p.put("openid.response_nonce", new String[]{"2020-01-01T00:00:00Zold"})),
                new Case("mode cancel", p -> p.put("openid.mode", new String[]{"cancel"})),
                new Case("imzali alan eksik", p -> p.put("openid.signed", new String[]{"signed,claimed_id"})),
                new Case("cogaltilmis claimed_id", p -> p.put("openid.claimed_id", new String[]{
                        STEAM_ID_PREFIX + newSteamId(), STEAM_ID_PREFIX + newSteamId()})));

        for (Case current : cases) {
            assertThat(fragment(steamCallback(newSteamId(), current.mutation()))).as(current.name())
                    .isEqualTo("error=EXTERNAL_AUTH_FAILED");
        }
        assertThat(PROVIDERS.steamVerifications).isEmpty();
    }

    @Test
    void stateCookieyeBagliVeTekKullanimlikDir() throws Exception {
        Started started = start(AuthApiPaths.STEAM_START);
        String steamId = newSteamId();

        MvcResult noCookie = mockMvc.perform(callbackRequest(started.state(), steamId, p -> { })).andReturn();
        assertThat(fragment(noCookie)).isEqualTo("error=EXTERNAL_AUTH_FAILED");

        MvcResult ok = mockMvc.perform(callbackRequest(started.state(), steamId, p -> { }).cookie(started.stateCookie()))
                .andReturn();
        assertThat(fragment(ok)).startsWith("status=registration_required");
        MvcResult replay = mockMvc.perform(callbackRequest(started.state(), newSteamId(), p -> { })
                .cookie(started.stateCookie())).andReturn();
        assertThat(fragment(replay)).as("replayed state").isEqualTo("error=EXTERNAL_AUTH_FAILED");
    }

    @Test
    void kayitliEpostaIcinHesapAcilmazYanitDegismezTokenTuketilir() throws Exception {
        String existing = registerActiveAccount(uniqueEmail(), uniqueUsername());
        String token = fragmentValue(steamCallback(newSteamId(), p -> { }), "token");

        complete(token, existing.toUpperCase(), uniqueUsername()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nextStep").value("VERIFY_EMAIL"));

        assertThat(accountCount(existing)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider = 'STEAM' "
                + "and account_id = ?", Integer.class, accountId(existing))).isZero();
        complete(token, uniqueEmail(), uniqueUsername()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));
    }

    @Test
    void kullaniciAdiCakismasi409GecersizVeSuresiDolmusTokenReddedilir() throws Exception {
        String taken = uniqueUsername();
        register(uniqueEmail(), taken, VALID_PASSWORD).andExpect(status().isAccepted());
        String token = fragmentValue(steamCallback(newSteamId(), p -> { }), "token");

        complete(token, uniqueEmail(), taken.toUpperCase()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        complete("forged-" + uniqueSuffix(), uniqueEmail(), uniqueUsername()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));

        jdbc.update("update steam_registrations set created_at = created_at - interval '1 hour', "
                + "expires_at = expires_at - interval '1 hour'");
        complete(token, uniqueEmail(), uniqueUsername()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));
    }

    @Test
    void baglantiliHesapZorunluRoldeTotpYoksaOturumAlamaz(CapturedOutput output) throws Exception {
        String steamId = newSteamId();
        String email = uniqueEmail();
        String token = fragmentValue(steamCallback(steamId, p -> { }), "token");
        complete(token, email, uniqueUsername()).andExpect(status().isAccepted());
        jdbc.update("update accounts set status = 'ACTIVE', email_verified_at = now() where email_normalized = lower(?)",
                email);
        grantRole(accountId(email), "MODERATOR");

        MvcResult result = steamCallback(steamId, p -> { });

        assertThat(fragment(result)).isEqualTo("error=TWO_FACTOR_ENROLLMENT_REQUIRED");
        assertThat(result.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        assertThat(output.getAll()).doesNotContain(token).doesNotContain(steamId).doesNotContainIgnoringCase(email);
    }

    @Test
    void steamHesabiDogrulamasiSahipKanitiOlmadanYapilamazKanitTekKullanimlikVeSteamIdyeBagli() throws Exception {
        String steamId = newSteamId();
        String email = uniqueEmail();
        String registrationToken = fragmentValue(steamCallback(steamId, p -> { }), "token");
        String proof = proofOf(complete(registrationToken, email, uniqueUsername()).andExpect(status().isAccepted()));
        String code = mailPort.awaitLatestCode(email, 1);
        String otherProof = proofOf(complete(fragmentValue(steamCallback(newSteamId(), p -> { }), "token"),
                uniqueEmail(), uniqueUsername()).andExpect(status().isAccepted()));
        String unusedRegistrationToken = fragmentValue(steamCallback(newSteamId(), p -> { }), "token");

        confirmWithoutProof(email, code).andExpect(status().isBadRequest());
        confirmWithPassword(email, code, VALID_PASSWORD).andExpect(status().isBadRequest());
        confirmWithProof(email, code, otherProof).andExpect(status().isBadRequest());
        confirmWithProof(email, code, unusedRegistrationToken).andExpect(status().isBadRequest());
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");

        confirmWithProof(email, code, proof).andExpect(status().isNoContent());
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from steam_registrations where steam_id = ? "
                + "and purpose = 'EMAIL_VERIFICATION' and consumed_at is not null", Integer.class, steamId))
                .isEqualTo(1);
    }

    @Test
    void kayitliEpostaIcinDonenKanitDogrulamayaYaramaz() throws Exception {
        String existing = registerActiveAccount(uniqueEmail(), uniqueUsername());
        String token = fragmentValue(steamCallback(newSteamId(), p -> { }), "token");

        String proof = proofOf(complete(token, existing, uniqueUsername()).andExpect(status().isAccepted()));

        assertThat(proof).as("yanıt biçimi hesap varlığını sızdırmaz").isNotBlank();
        confirmWithProof(existing, "000000", proof).andExpect(status().isBadRequest());
    }

    private static String proofOf(ResultActions result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.verificationProof");
    }

    private MvcResult steamCallback(String steamId, Consumer<Map<String, String[]>> mutation) throws Exception {
        Started started = start(AuthApiPaths.STEAM_START);
        return mockMvc.perform(callbackRequest(started.state(), steamId, mutation).cookie(started.stateCookie()))
                .andExpect(status().isFound()).andReturn();
    }

    private MockHttpServletRequestBuilder callbackRequest(String state, String steamId,
                                                          Consumer<Map<String, String[]>> mutation) {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("state", new String[]{state});
        params.put("openid.ns", new String[]{"http://specs.openid.net/auth/2.0"});
        params.put("openid.mode", new String[]{"id_res"});
        params.put("openid.op_endpoint", new String[]{PROVIDERS.url("/steam/openid")});
        params.put("openid.claimed_id", new String[]{STEAM_ID_PREFIX + steamId});
        params.put("openid.identity", new String[]{STEAM_ID_PREFIX + steamId});
        params.put("openid.return_to", new String[]{PUBLIC_BASE + AuthApiPaths.STEAM_CALLBACK + "?state=" + state});
        params.put("openid.response_nonce", new String[]{Instant.now().toString().substring(0, 19) + "Z" + uniqueSuffix()});
        params.put("openid.assoc_handle", new String[]{"1234567890"});
        params.put("openid.signed", new String[]{
                "signed,op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle"});
        params.put("openid.sig", new String[]{"c2lnbmF0dXJl"});
        mutation.accept(params);
        MockHttpServletRequestBuilder request = get(AuthApiPaths.STEAM_CALLBACK);
        params.forEach(request::param);
        return request;
    }

    private ResultActions complete(String token, String email, String username)
            throws Exception {
        return postJson(AuthApiPaths.STEAM_COMPLETE, """
                {"registrationToken":"%s","email":"%s","username":"%s"}
                """.formatted(token, email, username));
    }

    private static String fragmentValue(MvcResult result, String name) {
        return java.util.Arrays.stream(fragment(result).split("&")).filter(pair -> pair.startsWith(name + "="))
                .map(pair -> pair.substring(name.length() + 1)).findFirst().orElseThrow();
    }

    private static String newSteamId() {
        return "7656119" + String.format("%010d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 10_000_000_000L);
    }
}
