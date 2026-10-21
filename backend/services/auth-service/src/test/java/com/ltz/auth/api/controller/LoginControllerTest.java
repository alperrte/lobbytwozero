package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;

import jakarta.servlet.http.Cookie;

@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LoginControllerTest extends IntegrationTestSupport {

    private static final int MAX_FAILURES = 5;
    private static final String WRONG_PASSWORD = "definitely-not-the-password";

    @Test
    void eMailVeyaKullaniciAdiylaGirisHttpOnlyCookieIleOturumAcar() throws Exception {
        String username = uniqueUsername();
        String email = registerActiveAccount(uniqueEmail(), username);

        MvcResult byEmail = login(email.toUpperCase(), VALID_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andReturn();
        login(username.toUpperCase(), VALID_PASSWORD).andExpect(status().isOk());

        MockCookie access = (MockCookie) byEmail.getResponse().getCookie(ACCESS_COOKIE);
        MockCookie refresh = (MockCookie) byEmail.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(access).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getSecure()).isTrue();
        assertThat(access.getSameSite()).isEqualTo("Lax");
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getSecure()).isTrue();
        assertThat(refresh.getSameSite()).isEqualTo("Strict");
        assertThat(refresh.getPath()).isEqualTo(AuthApiPaths.SESSION);
        assertThat(byEmail.getResponse().getContentAsString()).doesNotContain(access.getValue(), refresh.getValue());
        UUID accountId = accountId(email);
        assertThat(securityEventCount(accountId, "LOGIN_SUCCEEDED")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens rt join auth_sessions s on s.id = rt.session_id "
                + "where s.account_id = ? and rt.token_hash = ?", Integer.class, accountId, refresh.getValue()))
                .as("refresh token is stored only as hash").isZero();
    }

    @Test
    void yanlisParolaVeBilinmeyenHesapAyniYanitiAlir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());

        String wrongPassword = login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getContentAsString();
        String unknownAccount = login(uniqueEmail(), WRONG_PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownAccount).isEqualTo(wrongPassword).contains("INVALID_CREDENTIALS");
        assertThat(securityEventCount(accountId(email), "LOGIN_FAILED")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "BANNED", "DISABLED", "DELETION_REQUESTED"})
    void aktifOlmayanHesapDogruParolaylaBileOturumAlamaz(String accountStatus) throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        jdbc.update("update accounts set status = ? where email_normalized = lower(?)", accountStatus, email);

        login(email, VALID_PASSWORD).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void besHatadanSonraKimlikKilitlenir_dogruParolaDaReddedilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        for (int attempt = 0; attempt < MAX_FAILURES; attempt++) {
            login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        login(email, VALID_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void bilinmeyenKimlikDeAyniSekildeKilitlenir_varlikSizmaz() throws Exception {
        String unknown = uniqueEmail();
        for (int attempt = 0; attempt < MAX_FAILURES; attempt++) {
            login(unknown, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        login(unknown, WRONG_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void basariliGirisHataSayaciniSifirlar() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        for (int attempt = 0; attempt < MAX_FAILURES - 1; attempt++) {
            login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login(email, VALID_PASSWORD).andExpect(status().isOk());

        login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void eszamanliDenemelerSiniriAsamaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());

        List<Integer> statuses = ConcurrentRequests.run(MAX_FAILURES * 2,
                index -> () -> login(email, WRONG_PASSWORD).andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(status -> status == 401)).hasSize(MAX_FAILURES);
        assertThat(statuses.stream().filter(status -> status == 429)).hasSize(MAX_FAILURES);
    }

    @Test
    void csrfTokeniOlmadanGirisReddedilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());

        mockMvc.perform(post(AuthApiPaths.LOGIN).contentType(MediaType.APPLICATION_JSON).content("""
                        {"identifier":"%s","password":"%s"}
                        """.formatted(email, VALID_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void csrfCookieVeBaslikIleGercekTarayiciAkisiCalisir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        MvcResult csrf = mockMvc.perform(get(AuthApiPaths.CSRF)).andExpect(status().isNoContent()).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie(CSRF_COOKIE);
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();

        mockMvc.perform(post(AuthApiPaths.LOGIN).cookie(csrfCookie).header(CSRF_HEADER, csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(email, VALID_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void istekKimligiYanitaVeGuvenlikOlayinaYazilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        String requestId = "req-" + uniqueSuffix();

        mockMvc.perform(post(AuthApiPaths.LOGIN).with(xsrf()).header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON).content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(email, VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId));

        assertThat(jdbc.queryForObject("select count(*) from security_events where account_id = ? and request_id = ? "
                + "and type = 'LOGIN_SUCCEEDED'", Integer.class, accountId(email), requestId)).isEqualTo(1);
    }

    @Test
    void parolaVeTokenLoglaraYazilmaz(CapturedOutput output) throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        login(email, WRONG_PASSWORD);
        SessionCookiePair session = loginSession(email);

        assertThat(output.getAll()).doesNotContain(VALID_PASSWORD).doesNotContain(WRONG_PASSWORD)
                .doesNotContain(session.access().getValue()).doesNotContain(session.refresh().getValue())
                .doesNotContainIgnoringCase(email);
    }
}
