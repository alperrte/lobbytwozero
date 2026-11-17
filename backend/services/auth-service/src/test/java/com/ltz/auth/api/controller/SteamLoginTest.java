package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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

import com.ltz.auth.ConcurrentRequests;
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
    void ilkSteamGirisindaYalnizKullaniciAdiSorulurHesapEpostasizAcilirSonrakiGirisDogrudandir() throws Exception {
        String steamId = newSteamId();
        String username = uniqueUsername();

        MvcResult first = steamCallback(steamId, p -> { });
        String registrationToken = fragmentValue(first, "token");
        assertThat(fragment(first)).startsWith("status=registration_required&provider=steam&token=");
        assertThat(first.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        assertThat(PROVIDERS.steamVerifications).singleElement().satisfies(sent -> {
            assertThat(sent).containsEntry("openid.mode", "check_authentication").containsKey("openid.sig")
                    .containsEntry("openid.claimed_id", STEAM_ID_PREFIX + steamId);
        });
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, steamId)).as("kullanıcı adı seçilene kadar hesap yok").isZero();

        MvcResult done = completeExternal(AuthApiPaths.STEAM_COMPLETE, registrationToken, username)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED")).andReturn();

        assertThat(done.getResponse().getContentAsString()).doesNotContain(registrationToken);
        assertThat(done.getResponse().getCookie(ACCESS_COOKIE)).isNotNull();
        assertThat(done.getResponse().getCookie(REFRESH_COOKIE)).isNotNull();
        UUID accountId = accountIdByUsername(username);
        var row = jdbc.queryForMap("select email, email_normalized, status, password_hash, email_verified_at "
                + "from accounts where id = ?", accountId);
        assertThat(row.get("email")).as("Steam e-posta vermez, sahte adres üretilmez").isNull();
        assertThat(row.get("email_normalized")).isNull();
        assertThat(row).containsEntry("status", "ACTIVE");
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("email_verified_at")).isNull();
        assertThat(jdbc.queryForObject("select provider_subject from external_identities where account_id = ? "
                + "and provider = 'STEAM'", String.class, accountId)).isEqualTo(steamId);
        assertThat(jdbc.queryForList("select r.code from account_roles ar join roles r on r.id = ar.role_id "
                + "where ar.account_id = ?", String.class, accountId)).containsExactly("PLAYER");
        assertThat(SignedJWT.parse(done.getResponse().getCookie(ACCESS_COOKIE).getValue()).getJWTClaimsSet()
                .getStringListClaim("amr")).containsExactly("fed");

        // Sonraki giriş: kullanıcı adı yeniden sorulmaz, doğrudan oturum.
        MvcResult login = steamCallback(steamId, p -> { });
        assertThat(fragment(login)).isEqualTo("status=authenticated");
        assertThat(login.getResponse().getCookie(ACCESS_COOKIE)).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, steamId)).isEqualTo(1);
    }

    @Test
    void istemciEpostaGonderemezBilinmeyenAlanReddedilirHesapEpostasizAcilir() throws Exception {
        String token = fragmentValue(steamCallback(newSteamId(), p -> { }), "token");
        String username = uniqueUsername();

        mockMvc.perform(post(AuthApiPaths.STEAM_COMPLETE).with(xsrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"%s\",\"username\":\"%s\",\"email\":\"saldirgan@example.test\"}"
                                .formatted(token, username)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from accounts where username_normalized = lower(?)",
                Integer.class, username)).isZero();

        completeExternal(AuthApiPaths.STEAM_COMPLETE, token, username).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select email from accounts where id = ?", String.class,
                accountIdByUsername(username))).isNull();
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

        String steamId = newSteamId();

        MvcResult result = steamCallback(steamId, p -> { });

        assertThat(fragment(result)).isEqualTo("error=EXTERNAL_AUTH_FAILED");
        // Diğer testlerin bıraktığı satırlardan bağımsız: yalnız bu SteamID için kayıt açılmamış olmalı.
        assertThat(jdbc.queryForObject("select count(*) from steam_registrations where steam_id = ?", Integer.class,
                steamId)).isZero();
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
    void kullaniciAdiCakismasi409GecersizVeSuresiDolmusTokenReddedilirCakismadaTokenTuketilmez() throws Exception {
        String taken = uniqueUsername();
        register(uniqueEmail(), taken, VALID_PASSWORD).andExpect(status().isAccepted());
        String token = fragmentValue(steamCallback(newSteamId(), p -> { }), "token");

        completeExternal(AuthApiPaths.STEAM_COMPLETE, token, taken.toUpperCase()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        completeExternal(AuthApiPaths.STEAM_COMPLETE, "forged-" + uniqueSuffix(), uniqueUsername())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));
        completeExternal(AuthApiPaths.STEAM_COMPLETE, token, "ab").andExpect(status().isBadRequest());

        // Çakışma token'ı tüketmedi: başka adla devam edilebilir; ikinci kullanım reddedilir.
        completeExternal(AuthApiPaths.STEAM_COMPLETE, token, uniqueUsername()).andExpect(status().isOk());
        completeExternal(AuthApiPaths.STEAM_COMPLETE, token, uniqueUsername()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));

        String expiring = fragmentValue(steamCallback(newSteamId(), p -> { }), "token");
        jdbc.update("update steam_registrations set created_at = created_at - interval '1 hour', "
                + "expires_at = expires_at - interval '1 hour'");
        completeExternal(AuthApiPaths.STEAM_COMPLETE, expiring, uniqueUsername()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));
    }

    @Test
    void kayitTamamlamaCsrfKorumalidirVeAyniTokenlaEszamanliIstekteTekHesapOlusur() throws Exception {
        String steamId = newSteamId();
        String token = fragmentValue(steamCallback(steamId, p -> { }), "token");

        postJson(AuthApiPaths.STEAM_COMPLETE, "{\"registrationToken\":\"%s\",\"username\":\"%s\"}"
                .formatted(token, uniqueUsername())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, steamId)).isZero();

        ConcurrentRequests.run(6, index -> (Callable<Integer>) () -> completeExternal(AuthApiPaths.STEAM_COMPLETE,
                token, uniqueUsername()).andReturn().getResponse().getStatus());

        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, steamId)).isEqualTo(1);
    }

    @Test
    void baglantiliHesapZorunluRoldeTotpYoksaOturumAlamaz(CapturedOutput output) throws Exception {
        String steamId = newSteamId();
        String username = uniqueUsername();
        String token = fragmentValue(steamCallback(steamId, p -> { }), "token");
        completeExternal(AuthApiPaths.STEAM_COMPLETE, token, username).andExpect(status().isOk());
        grantRole(accountIdByUsername(username), "MODERATOR");

        MvcResult result = steamCallback(steamId, p -> { });

        assertThat(fragment(result)).isEqualTo("error=TWO_FACTOR_ENROLLMENT_REQUIRED");
        assertThat(result.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        assertThat(output.getAll()).doesNotContain(token).doesNotContain(steamId);
    }

    @Test
    void epostasizHesapEpostaGerektirenIslemlerdeAcikHataVerirSahteBasariVermez() throws Exception {
        String username = uniqueUsername();
        String token = fragmentValue(steamCallback(newSteamId(), p -> { }), "token");
        completeExternal(AuthApiPaths.STEAM_COMPLETE, token, username).andExpect(status().isOk());
        UUID accountId = accountIdByUsername(username);
        enableTotp(accountId);

        // Parola belirleme kodu e-postayla gider: e-postasız hesapta istenemez.
        mockMvc.perform(post(AuthApiPaths.PASSWORD_SET_REQUEST).header("Authorization", bearerFor(accountId))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(currentTotpCode(accountId))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_REQUIRED"));
        // Steam ile açılan hesap C türüdür: kendi başına silme başlatamaz, yöneticiye talep açar (e-posta gerekmez).
        mockMvc.perform(post(AuthApiPaths.ACCOUNT_DELETION_REQUEST).header("Authorization", bearerFor(accountId))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(currentTotpCode(accountId))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DELETION_REQUIRES_SUPPORT"));
        assertThat(jdbc.queryForObject("select count(*) from account_deletion_tokens where account_id = ?",
                Integer.class, accountId)).isZero();
        // Parola sıfırlama adresle bulunur; bu hesabın adresi olmadığı için erişilemez.
        assertThat(mailPort).isNotNull();
    }

    @Test
    void epostasizSteamHesabiTotpIleYenidenDogrulayipSonradanEpostaEkleyebilirAdresKodlaDogrulanir() throws Exception {
        String username = uniqueUsername();
        completeExternal(AuthApiPaths.STEAM_COMPLETE, fragmentValue(steamCallback(newSteamId(), p -> { }), "token"), username)
                .andExpect(status().isOk());
        UUID accountId = accountIdByUsername(username);
        String newEmail = uniqueEmail();

        // TOTP yoksa yeniden doğrulama yapılamaz.
        mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE).header("Authorization", bearerFor(accountId))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"%s\"}".formatted(newEmail)))
                .andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId)).isNull();

        enableTotp(accountId);
        mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE).header("Authorization", bearerFor(accountId))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"%s\",\"code\":\"%s\"}".formatted(newEmail, currentTotpCode(accountId))))
                .andExpect(status().isAccepted());
        String code = mailPort.awaitLatestCode(newEmail, 1);
        assertThat(jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId))
                .as("onaydan önce adres eklenmez").isNull();

        mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE_CONFIRM).header("Authorization", bearerFor(accountId))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(code)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId))
                .isEqualToIgnoringCase(newEmail);
        assertThat(jdbc.queryForObject("select email_verified_at is not null from accounts where id = ?", Boolean.class,
                accountId)).isTrue();
    }

    @Test
    void eskiBekleyenSteamHesabiOturumAlamazDogrulamaSahipKanitiIsterKanitTekKullanimlikVeSteamIdyeBagli() throws Exception {
        String steamId = newSteamId();
        String email = uniqueEmail();
        legacyPendingSteamAccount(steamId, email);
        String code = mailPort.awaitLatestCode(email, 1);

        MvcResult pending = steamCallback(steamId, p -> { });
        assertThat(fragment(pending)).as("e-posta doğrulanmadan oturum yok")
                .startsWith("status=email_verification_required&proof=");
        assertThat(pending.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        String proof = fragmentValue(pending, "proof");

        String otherSteamId = newSteamId();
        legacyPendingSteamAccount(otherSteamId, uniqueEmail());
        String otherProof = fragmentValue(steamCallback(otherSteamId, p -> { }), "proof");
        String registrationToken = fragmentValue(steamCallback(newSteamId(), p -> { }), "token");

        confirmWithoutProof(email, code).andExpect(status().isBadRequest());
        confirmWithPassword(email, code, VALID_PASSWORD).andExpect(status().isBadRequest());
        confirmWithProof(email, code, otherProof).andExpect(status().isBadRequest());
        confirmWithProof(email, code, registrationToken).andExpect(status().isBadRequest());
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");

        confirmWithProof(email, code, proof).andExpect(status().isNoContent());
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from steam_registrations where steam_id = ? "
                + "and purpose = 'EMAIL_VERIFICATION' and consumed_at is not null", Integer.class, steamId))
                .isEqualTo(1);
        assertThat(fragment(steamCallback(steamId, p -> { }))).isEqualTo("status=authenticated");
    }

    /** Eski akışla (e-posta + doğrulama) açılmış, henüz doğrulanmamış Steam hesabı; yeni hesaplar böyle açılmaz. */
    private void legacyPendingSteamAccount(String steamId, String email) throws Exception {
        UUID id = UUID.randomUUID();
        String username = uniqueUsername();
        jdbc.update("insert into accounts (id, email, email_normalized, username, username_normalized, status, "
                + "created_at, updated_at) values (?, ?, lower(?), ?, lower(?), 'PENDING_VERIFICATION', now(), now())",
                id, email, email, username, username);
        jdbc.update("insert into account_roles (account_id, role_id) select ?, id from roles where code = 'PLAYER'", id);
        jdbc.update("insert into external_identities (id, account_id, provider, provider_subject, linked_at) "
                + "values (gen_random_uuid(), ?, 'STEAM', ?, now())", id, steamId);
        resend(email).andExpect(status().isAccepted());
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

    private static String newSteamId() {
        return "7656119" + String.format("%010d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 10_000_000_000L);
    }
}
