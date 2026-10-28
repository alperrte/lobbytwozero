package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void yeniHesapAcarOturumVerirPkceDogrulanirSirlarLogaYazilmaz(CapturedOutput output) throws Exception {
        String subject = "g-" + uniqueSuffix();
        String email = uniqueEmail();
        Started started = start(AuthApiPaths.GOOGLE_START);
        Map<String, String> query = query(started.location());
        String code = "code-" + uniqueSuffix();
        PROVIDERS.idTokensByCode.put(code, googleIdToken(subject, email, true, query.get("nonce")));

        MvcResult result = callback(code, started);

        assertThat(fragment(result)).isEqualTo("status=authenticated");
        assertThat(result.getResponse().getCookie(ACCESS_COOKIE)).isNotNull();
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE)).isNotNull();
        assertThat(result.getResponse().getCookie(OAUTH_STATE_COOKIE).getMaxAge()).isZero();
        Map<String, String> tokenRequest = PROVIDERS.tokenRequestsByCode.get(code);
        assertThat(tokenRequest).containsEntry("client_id", GOOGLE_CLIENT_ID)
                .containsEntry("client_secret", GOOGLE_CLIENT_SECRET)
                .containsEntry("redirect_uri", PUBLIC_BASE + AuthApiPaths.GOOGLE_CALLBACK)
                .containsEntry("grant_type", "authorization_code");
        assertThat(s256(tokenRequest.get("code_verifier"))).isEqualTo(query.get("code_challenge"));
        UUID accountId = accountId(email);
        assertThat(accountStatus(email)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select password_hash is null from accounts where id = ?", Boolean.class, accountId))
                .isTrue();
        assertThat(jdbc.queryForList("select r.code from account_roles ar join roles r on r.id = ar.role_id "
                + "where ar.account_id = ?", String.class, accountId)).containsExactly("PLAYER");
        assertThat(jdbc.queryForObject("select provider_subject from external_identities where account_id = ? "
                + "and provider = 'GOOGLE'", String.class, accountId)).isEqualTo(subject);
        String access = result.getResponse().getCookie(ACCESS_COOKIE).getValue();
        assertThat(SignedJWT.parse(access).getJWTClaimsSet().getStringListClaim("amr")).containsExactly("fed");
        assertThat(output.getAll()).doesNotContain(started.state()).doesNotContain(code).doesNotContain(subject)
                .doesNotContain(GOOGLE_CLIENT_SECRET).doesNotContainIgnoringCase(email);
    }

    @Test
    void ayniGoogleKimligiIkinciGiristeYeniHesapAcmaz() throws Exception {
        String subject = "g-" + uniqueSuffix();
        String email = uniqueEmail();
        completeLogin(subject, email, true);

        MvcResult second = completeLogin(subject, email, true);

        assertThat(fragment(second)).isEqualTo("status=authenticated");
        assertThat(accountCount(email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, subject)).isEqualTo(1);
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
        assertThat(fragment(callback(code, started))).isEqualTo("status=authenticated");
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
        completeLogin(suspendedSubject, suspended, true);
        jdbc.update("update accounts set status = 'SUSPENDED' where email_normalized = lower(?)", suspended);

        MvcResult suspendedResult = completeLogin(suspendedSubject, suspended, true);

        assertThat(fragment(suspendedResult)).isEqualTo("error=ACCOUNT_SUSPENDED");
        assertThat(suspendedResult.getResponse().getCookie(ACCESS_COOKIE)).isNull();

        String adminSubject = "g-" + uniqueSuffix();
        String admin = uniqueEmail();
        completeLogin(adminSubject, admin, true);
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

        ConcurrentRequests.run(parallel,
                index -> (Callable<Integer>) () -> callback(codes.get(index), flows.get(index)).getResponse().getStatus());

        assertThat(accountCount(email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from external_identities where provider_subject = ?",
                Integer.class, subject)).isEqualTo(1);
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
