package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.ExternalProviderTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.http.Cookie;

@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class GoogleLoginTest extends ExternalProviderTestSupport {

    @BeforeEach
    void resetProviders() {
        PROVIDERS.reset();
    }

    @Test
    void baslangicPkceStateNonceVeHttpOnlyStateCookieIleYonlendirir() throws Exception {
        MvcResult result = mockMvc.perform(get(AuthApiPaths.GOOGLE_START)).andExpect(status().isFound()).andReturn();

        Map<String, String> query = query(URI.create(result.getResponse().getHeader("Location")));
        assertThat(query).containsEntry("response_type", "code").containsEntry("client_id", GOOGLE_CLIENT_ID)
                .containsEntry("redirect_uri", PUBLIC_BASE + AuthApiPaths.GOOGLE_CALLBACK)
                .containsEntry("scope", "openid email").containsEntry("code_challenge_method", "S256")
                .containsKeys("state", "nonce", "code_challenge");
        MockCookie cookie = (MockCookie) result.getResponse().getCookie(OAUTH_STATE_COOKIE);
        assertThat(cookie.getValue()).isEqualTo(query.get("state"));
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(jdbc.queryForObject("select count(*) from oauth_flows where state_hash = encode(sha256(convert_to(?, 'UTF8')), 'hex')",
                Integer.class, query.get("state"))).as("state is stored only as hash").isEqualTo(1);
    }

    @Test
    void ilkGirisHesapAcmazKullaniciAdiSecilinceHesapVeOturumOlusurPkceDogrulanirSirlarLogaYazilmaz(CapturedOutput output)
            throws Exception {
        String subject = "g-" + uniqueSuffix();
        String email = uniqueEmail();
        String username = uniqueUsername();
        Started started = start(AuthApiPaths.GOOGLE_START);
        Map<String, String> query = query(started.location());
        String code = "code-" + uniqueSuffix();
        PROVIDERS.idTokensByCode.put(code, googleIdToken(subject, email, true, query.get("nonce")));

        MvcResult result = callback(code, started);

        // Kullanıcı adı seçilene kadar hesap da oturum da yoktur; yalnız tek kullanımlık kayıt token'ı döner.
        assertThat(fragment(result)).startsWith("status=registration_required&provider=google&token=");
        String token = fragmentValue(result, "token");
        assertThat(result.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE)).isNull();
        assertThat(result.getResponse().getCookie(OAUTH_STATE_COOKIE).getMaxAge()).isZero();
        assertThat(accountCount(email)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, subject)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from google_registrations where token_hash = "
                + "encode(sha256(convert_to(?, 'UTF8')), 'hex')", Integer.class, token)).as("token yalnız özet").isEqualTo(1);
        Map<String, String> tokenRequest = PROVIDERS.tokenRequestsByCode.get(code);
        assertThat(tokenRequest).containsEntry("client_id", GOOGLE_CLIENT_ID)
                .containsEntry("client_secret", GOOGLE_CLIENT_SECRET)
                .containsEntry("redirect_uri", PUBLIC_BASE + AuthApiPaths.GOOGLE_CALLBACK)
                .containsEntry("grant_type", "authorization_code");
        assertThat(s256(tokenRequest.get("code_verifier"))).isEqualTo(query.get("code_challenge"));

        MvcResult done = completeExternal(AuthApiPaths.GOOGLE_COMPLETE, token, username).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED")).andReturn();

        assertThat(done.getResponse().getCookie(ACCESS_COOKIE)).isNotNull();
        assertThat(done.getResponse().getCookie(REFRESH_COOKIE)).isNotNull();
        UUID accountId = accountId(email);
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select username from accounts where id = ?", String.class, accountId))
                .isEqualTo(username);
        assertThat(jdbc.queryForObject("select password_hash is null from accounts where id = ?", Boolean.class, accountId))
                .isTrue();
        assertThat(jdbc.queryForObject("select email_verified_at is not null from accounts where id = ?", Boolean.class,
                accountId)).as("e-posta Google tarafından doğrulanmış").isTrue();
        assertThat(jdbc.queryForList("select r.code from account_roles ar join roles r on r.id = ar.role_id "
                + "where ar.account_id = ?", String.class, accountId)).containsExactly("PLAYER");
        assertThat(jdbc.queryForObject("select provider_subject from external_identities where account_id = ? "
                + "and provider = 'GOOGLE'", String.class, accountId)).isEqualTo(subject);
        String access = done.getResponse().getCookie(ACCESS_COOKIE).getValue();
        assertThat(SignedJWT.parse(access).getJWTClaimsSet().getStringListClaim("amr")).containsExactly("fed");
        assertThat(output.getAll()).doesNotContain(started.state()).doesNotContain(code).doesNotContain(subject)
                .doesNotContain(token).doesNotContain(GOOGLE_CLIENT_SECRET).doesNotContainIgnoringCase(email);
    }

    @Test
    void kayitTokeniTekKullanimlikSahteyaDaSuresiDolmusTokenReddedilirIkinciHesapAcilmaz() throws Exception {
        String email = uniqueEmail();
        String token = registrationToken(completeLogin("g-" + uniqueSuffix(), email, true));

        completeExternal(AuthApiPaths.GOOGLE_COMPLETE, token, uniqueUsername()).andExpect(status().isOk());
        completeExternal(AuthApiPaths.GOOGLE_COMPLETE, token, uniqueUsername()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));
        completeExternal(AuthApiPaths.GOOGLE_COMPLETE, "forged-" + uniqueSuffix(), uniqueUsername())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));
        assertThat(accountCount(email)).isEqualTo(1);

        String expiring = registrationToken(completeLogin("g-" + uniqueSuffix(), uniqueEmail(), true));
        jdbc.update("update google_registrations set created_at = created_at - interval '1 hour', "
                + "expires_at = expires_at - interval '1 hour'");
        completeExternal(AuthApiPaths.GOOGLE_COMPLETE, expiring, uniqueUsername()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));
    }

    @Test
    void kullaniciAdiCakismasi409VerirTokenTuketilmezBaskaAdlaDevamEdilir() throws Exception {
        String taken = uniqueUsername();
        register(uniqueEmail(), taken, VALID_PASSWORD).andExpect(status().isAccepted());
        String email = uniqueEmail();
        String token = registrationToken(completeLogin("g-" + uniqueSuffix(), email, true));

        completeExternal(AuthApiPaths.GOOGLE_COMPLETE, token, taken.toUpperCase()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        assertThat(accountCount(email)).isZero();

        completeExternal(AuthApiPaths.GOOGLE_COMPLETE, token, uniqueUsername()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        assertThat(accountCount(email)).isEqualTo(1);
    }

    @Test
    void gecersizKullaniciAdiVeIstemcidenGelenEpostaYaDaSaglayiciKimligiReddedilirHesapSunucuVerisiyleAcilir() throws Exception {
        String email = uniqueEmail();
        String subject = "g-" + uniqueSuffix();
        String token = registrationToken(completeLogin(subject, email, true));

        for (String bad : new String[]{"ab", "bu kullanici adi cok uzun ve gecersiz", "ad!soyad", ""}) {
            completeExternal(AuthApiPaths.GOOGLE_COMPLETE, token, bad).andExpect(status().isBadRequest());
        }
        // İstemci e-posta ya da sağlayıcı kimliği öne süremez: bilinmeyen alanlar isteği reddeder, hesap açılmaz.
        String username = uniqueUsername();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(AuthApiPaths.GOOGLE_COMPLETE).with(xsrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"registrationToken\":\"%s\",\"username\":\"%s\",\"email\":\"saldirgan@example.test\",\"sub\":\"baska-sub\"}"
                                .formatted(token, username)))
                .andExpect(status().isBadRequest());
        assertThat(accountCount(email)).isZero();

        completeExternal(AuthApiPaths.GOOGLE_COMPLETE, token, username).andExpect(status().isOk());

        assertThat(accountCount(email)).as("e-posta sunucudaki Google kaydından gelir").isEqualTo(1);
        assertThat(accountCount("saldirgan@example.test")).isZero();
        assertThat(jdbc.queryForObject("select provider_subject from external_identities where account_id = ? "
                + "and provider = 'GOOGLE'", String.class, accountId(email))).isEqualTo(subject);
    }

    @Test
    void kayitTamamlamaCsrfKorumalidirAcikOrijinliIstekHesapAcmaz() throws Exception {
        String email = uniqueEmail();
        String token = registrationToken(completeLogin("g-" + uniqueSuffix(), email, true));

        postJson(AuthApiPaths.GOOGLE_COMPLETE, "{\"registrationToken\":\"%s\",\"username\":\"%s\"}"
                .formatted(token, uniqueUsername())).andExpect(status().isForbidden());

        assertThat(accountCount(email)).isZero();
    }

    @Test
    void mevcutGoogleKullanicisiIkinciGiristeKullaniciAdiSorulmadanDogrudanGirer() throws Exception {
        String subject = "g-" + uniqueSuffix();
        String email = uniqueEmail();
        signUp(subject, email, uniqueUsername());

        MvcResult second = completeLogin(subject, email, true);

        assertThat(fragment(second)).isEqualTo("status=authenticated");
        assertThat(second.getResponse().getCookie(ACCESS_COOKIE)).isNotNull();
        assertThat(accountCount(email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, subject)).isEqualTo(1);
    }

    @Test
    void eskiOtomatikAdliGoogleHesabiBozulmazVeDogrudanGirer() throws Exception {
        String subject = "g-" + uniqueSuffix();
        String email = uniqueEmail();
        UUID legacy = UUID.randomUUID();
        String legacyName = "g_" + uniqueSuffix().replace("-", "").substring(0, 12);
        jdbc.update("insert into accounts (id, email, email_normalized, username, username_normalized, status, "
                + "email_verified_at, created_at, updated_at) values (?, ?, lower(?), ?, lower(?), 'ACTIVE', now(), now(), now())",
                legacy, email, email, legacyName, legacyName);
        jdbc.update("insert into account_roles (account_id, role_id) select ?, id from roles where code = 'PLAYER'", legacy);
        jdbc.update("insert into external_identities (id, account_id, provider, provider_subject, linked_at) "
                + "values (gen_random_uuid(), ?, 'GOOGLE', ?, now())", legacy, subject);

        MvcResult result = completeLogin(subject, email, true);

        assertThat(fragment(result)).isEqualTo("status=authenticated");
        assertThat(jdbc.queryForObject("select username from accounts where id = ?", String.class, legacy))
                .isEqualTo(legacyName);
    }

    @Test
    void stateTekKullanimlikVeCookieyeBagliDir() throws Exception {
        Started started = start(AuthApiPaths.GOOGLE_START);
        String code = "code-" + uniqueSuffix();
        PROVIDERS.idTokensByCode.put(code, googleIdToken("g-" + uniqueSuffix(), uniqueEmail(), true,
                query(started.location()).get("nonce")));

        assertThat(fragment(callbackWithState(code, started.state(), new Cookie(OAUTH_STATE_COOKIE, "other-browser"))))
                .isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(fragment(callbackWithState(code, started.state(), null))).isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(fragment(callback(code, started))).startsWith("status=registration_required&provider=google");
        assertThat(fragment(callback(code, started))).as("replayed state").isEqualTo("error=EXTERNAL_AUTH_FAILED");
        assertThat(fragment(callbackWithState(code, "never-issued", new Cookie(OAUTH_STATE_COOKIE, "never-issued"))))
                .isEqualTo("error=EXTERNAL_AUTH_FAILED");
    }

    @Test
    void gecersizIdTokenlarReddedilirHesapOlusmaz() throws Exception {
        KeyPair attacker = newKeyPair();
        record Case(String name, TokenFactory token) {
        }
        List<Case> cases = List.of(
                new Case("yanlis nonce", n -> googleIdToken("g1", uniqueEmail(), true, "forged-nonce")),
                new Case("yanlis audience", n -> googleIdToken(signingKey(), "g2", uniqueEmail(), true, n,
                        GOOGLE_ISSUER, "other-client", 300)),
                new Case("yanlis issuer", n -> googleIdToken(signingKey(), "g3", uniqueEmail(), true, n,
                        "https://evil.example.test", GOOGLE_CLIENT_ID, 300)),
                new Case("suresi dolmus", n -> googleIdToken(signingKey(), "g4", uniqueEmail(), true, n,
                        GOOGLE_ISSUER, GOOGLE_CLIENT_ID, -600)),
                new Case("baska anahtarla imzali", n -> googleIdToken(attacker, "g5", uniqueEmail(), true, n,
                        GOOGLE_ISSUER, GOOGLE_CLIENT_ID, 300)),
                new Case("imzasiz (alg=none)", n -> unsignedIdToken("g6", n)));
        int accountsBefore = jdbc.queryForObject("select count(*) from accounts", Integer.class);

        for (Case current : cases) {
            Started started = start(AuthApiPaths.GOOGLE_START);
            String code = "code-" + uniqueSuffix();
            PROVIDERS.idTokensByCode.put(code, current.token().create(query(started.location()).get("nonce")));

            MvcResult result = callback(code, started);

            assertThat(fragment(result)).as(current.name()).isEqualTo("error=EXTERNAL_AUTH_FAILED");
            assertThat(result.getResponse().getCookie(ACCESS_COOKIE)).as(current.name()).isNull();
        }
        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isEqualTo(accountsBefore);
    }

    @Test
    void tokenUcuHatasiBasarisizGirisSayilir() throws Exception {
        Started started = start(AuthApiPaths.GOOGLE_START);
        PROVIDERS.tokenEndpointFails = true;

        assertThat(fragment(callback("code-" + uniqueSuffix(), started))).isEqualTo("error=EXTERNAL_AUTH_FAILED");
    }

    @Test
    void saglayiciErisimiReddetmisseKodYoktur() throws Exception {
        Started started = start(AuthApiPaths.GOOGLE_START);

        MvcResult result = mockMvc.perform(get(AuthApiPaths.GOOGLE_CALLBACK).param("error", "access_denied")
                .param("state", started.state()).cookie(started.stateCookie())).andReturn();

        assertThat(fragment(result)).isEqualTo("error=EXTERNAL_AUTH_FAILED");
    }

    @Test
    void dogrulanmamisEpostaylaYeniHesapAcilmaz() throws Exception {
        String email = uniqueEmail();

        assertThat(fragment(completeLogin("g-" + uniqueSuffix(), email, false))).isEqualTo("error=EXTERNAL_EMAIL_UNVERIFIED");
        assertThat(accountCount(email)).isZero();
    }

    @Test
    void kayitliEpostaSessizceBirlestirilmez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        String subject = "g-" + uniqueSuffix();

        MvcResult result = completeLogin(subject, email.toUpperCase(), true);

        assertThat(fragment(result)).isEqualTo("error=ACCOUNT_LINK_REQUIRED");
        assertThat(result.getResponse().getCookie(ACCESS_COOKIE)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, subject)).isZero();
        assertThat(securityEventCount(accountId, "EXTERNAL_LINK_CONFLICT")).isEqualTo(1);
    }

    @Test
    void bagliHesapAskidaysaVeyaZorunluRoldeTotpYoksaOturumVerilmez() throws Exception {
        String suspendedSubject = "g-" + uniqueSuffix();
        String suspended = uniqueEmail();
        signUp(suspendedSubject, suspended, uniqueUsername());
        jdbc.update("update accounts set status = 'SUSPENDED' where email_normalized = lower(?)", suspended);

        MvcResult suspendedResult = completeLogin(suspendedSubject, suspended, true);

        assertThat(fragment(suspendedResult)).isEqualTo("error=ACCOUNT_SUSPENDED");
        assertThat(suspendedResult.getResponse().getCookie(ACCESS_COOKIE)).isNull();

        String adminSubject = "g-" + uniqueSuffix();
        String admin = uniqueEmail();
        signUp(adminSubject, admin, uniqueUsername());
        grantRole(accountId(admin), "ADMIN");

        MvcResult adminResult = completeLogin(adminSubject, admin, true);

        assertThat(fragment(adminResult)).isEqualTo("error=TWO_FACTOR_ENROLLMENT_REQUIRED");
        assertThat(adminResult.getResponse().getCookie(ACCESS_COOKIE)).isNull();
    }

    @Test
    void eszamanliIlkGirislerdenYalnizBirHesapOlusur() throws Exception {
        String subject = "g-" + uniqueSuffix();
        String email = uniqueEmail();
        int parallel = 6;
        List<Started> flows = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < parallel; index++) {
            Started started = start(AuthApiPaths.GOOGLE_START);
            String code = "code-" + uniqueSuffix();
            PROVIDERS.idTokensByCode.put(code, googleIdToken(subject, email, true, query(started.location()).get("nonce")));
            flows.add(started);
            codes.add(code);
        }
        List<String> tokens = new ArrayList<>();
        for (int index = 0; index < parallel; index++) {
            tokens.add(registrationToken(callback(codes.get(index), flows.get(index))));
        }

        ConcurrentRequests.run(parallel, index -> (Callable<Integer>) () -> completeExternal(AuthApiPaths.GOOGLE_COMPLETE,
                tokens.get(index), uniqueUsername()).andReturn().getResponse().getStatus());

        assertThat(accountCount(email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, subject)).isEqualTo(1);
    }

    @Test
    void ayniTokenlaEszamanliTamamlamadaYalnizBirHesapOlusur() throws Exception {
        String email = uniqueEmail();
        String token = registrationToken(completeLogin("g-" + uniqueSuffix(), email, true));

        ConcurrentRequests.run(6, index -> (Callable<Integer>) () -> completeExternal(AuthApiPaths.GOOGLE_COMPLETE,
                token, uniqueUsername()).andReturn().getResponse().getStatus());

        assertThat(accountCount(email)).isEqualTo(1);
    }

    /** İlk Google girişi + kullanıcı adı seçimi: hesabı gerçekten açar. */
    private void signUp(String subject, String email, String username) throws Exception {
        String token = registrationToken(completeLogin(subject, email, true));
        completeExternal(AuthApiPaths.GOOGLE_COMPLETE, token, username).andExpect(status().isOk());
    }

    private String registrationToken(MvcResult callbackResult) {
        assertThat(fragment(callbackResult)).startsWith("status=registration_required&provider=google&token=");
        return fragmentValue(callbackResult, "token");
    }

    private MvcResult completeLogin(String subject, String email, boolean emailVerified) throws Exception {
        Started started = start(AuthApiPaths.GOOGLE_START);
        String code = "code-" + uniqueSuffix();
        PROVIDERS.idTokensByCode.put(code, googleIdToken(subject, email, emailVerified, query(started.location()).get("nonce")));
        return callback(code, started);
    }

    private MvcResult callback(String code, Started started) throws Exception {
        return callbackWithState(code, started.state(), started.stateCookie());
    }

    private MvcResult callbackWithState(String code, String state, Cookie stateCookie) throws Exception {
        var request = get(AuthApiPaths.GOOGLE_CALLBACK).param("code", code).param("state", state);
        if (stateCookie != null) {
            request.cookie(stateCookie);
        }
        return mockMvc.perform(request).andExpect(status().isFound()).andReturn();
    }

    private static String s256(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    @FunctionalInterface
    private interface TokenFactory {
        String create(String nonce) throws Exception;
    }

}
