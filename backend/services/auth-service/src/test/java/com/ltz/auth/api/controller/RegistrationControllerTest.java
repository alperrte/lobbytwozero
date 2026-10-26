package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import com.ltz.auth.ConcurrentRequests;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;
import com.ltz.auth.domain.enums.VerificationPurpose;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

@Import(RecordingMailPortConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class RegistrationControllerTest extends IntegrationTestSupport {

    private static final int CONCURRENT_REQUESTS = 20;

    @Autowired
    private RecordingMailPort mailPort;

    @Autowired
    private VerificationCodeHasher codeHasher;

    @Test
    void kayitBekleyenHesapOlusturur_yalnizPlayerRoluVerir_sirlariHashler() throws Exception {
        String email = uniqueEmail();

        register(email, uniqueUsername(), VALID_PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"nextStep\":\"VERIFY_EMAIL\"}", true));

        UUID accountId = accountId(email);
        assertThat(accountStatus(email)).isEqualTo("PENDING_VERIFICATION");
        String passwordHash = jdbc.queryForObject(
                "select password_hash from accounts where id = ?", String.class, accountId);
        assertThat(passwordHash).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(VALID_PASSWORD);
        assertThat(jdbc.queryForList("""
                select r.code from account_roles ar join roles r on r.id = ar.role_id where ar.account_id = ?
                """, String.class, accountId)).containsExactly("PLAYER");

        String code = mailPort.awaitLatestCode(email, 1);
        assertThat(code).matches("^[0-9]{6}$");
        String storedHash = jdbc.queryForObject(
                "select code_hash from email_verification_codes where account_id = ?", String.class, accountId);
        assertThat(storedHash).isNotEqualTo(code).hasSize(64)
                .isEqualTo(codeHasher.hash(accountId, VerificationPurpose.EMAIL_VERIFICATION, code));
        assertThat(securityEventCount(accountId, "ACCOUNT_REGISTERED")).isEqualTo(1);
    }

    @Test
    void kayitliEPostaBuyukKucukHarfFarkiylaTekrarKaydedilemez_yanitAyniKalir(CapturedOutput output)
            throws Exception {
        String email = uniqueEmail();
        String firstBody = register(email, uniqueUsername(), VALID_PASSWORD)
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        String secondBody = register(email.toUpperCase(), uniqueUsername(), VALID_PASSWORD)
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(secondBody).isEqualTo(firstBody);
        assertThat(accountCount(email)).isEqualTo(1);
        assertThat(securityEventCount(accountId(email), "REGISTRATION_EMAIL_CONFLICT")).isEqualTo(1);
        assertThat(mailPort.awaitLatestCode(email, 1)).isNotBlank();
        assertThat(mailPort.deliveredTo(email)).hasSize(1);
        // Unique ihlalinde PostgreSQL'in "Key (email_normalized)=(...)" ayrıntısı log'a düşmemeli.
        assertThat(output.getAll()).doesNotContainIgnoringCase(email);
    }

    @Test
    void kullaniciAdiBuyukKucukHarfFarkiylaCakisirsa409Doner() throws Exception {
        String username = uniqueUsername();
        register(uniqueEmail(), username, VALID_PASSWORD).andExpect(status().isAccepted());

        register(uniqueEmail(), username.toUpperCase(), VALID_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void zayifVeyaKisaParolaylaKayitReddedilirHesapOlusmaz() throws Exception {
        String email = uniqueEmail();
        String username = uniqueUsername();

        register(email, username, "passwordpassword1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        // 12-14 karakter artık yetmez (asgari 15).
        register(email, username, "a1b2-c3d4-e5f6").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        assertThat(accountCount(email)).isZero();
    }

    static List<Arguments> invalidRegistrations() {
        String validEmail = "valid@example.test";
        return List.of(
                Arguments.of("gecersiz e-posta", "not-an-email", "valid_name", VALID_PASSWORD, "email"),
                Arguments.of("cok uzun e-posta", "a".repeat(250) + "@x.io", "valid_name", VALID_PASSWORD, "email"),
                Arguments.of("kisa kullanici adi", validEmail, "ab", VALID_PASSWORD, "username"),
                Arguments.of("uzun kullanici adi", validEmail, "a".repeat(21), VALID_PASSWORD, "username"),
                Arguments.of("izinsiz karakter", validEmail, "bad-name!", VALID_PASSWORD, "username"),
                Arguments.of("kisa parola", validEmail, "valid_name", "a".repeat(14), "password"),
                Arguments.of("uzun parola", validEmail, "valid_name", "a".repeat(129), "password"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrations")
    void gecersizGirdiAlanHatasiylaReddedilir_degerYansitilmaz(String caseName, String email, String username,
                                                             String password, String field) throws Exception {
        String body = register(email, username, password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(password);
        assertThat(accountCount(email)).isZero();
    }

    @Test
    void istemciRolVeDurumGonderemez() throws Exception {
        String email = uniqueEmail();

        postJson(AuthApiPaths.REGISTER, """
                {"email":"%s","username":"%s","password":"%s","role":"ADMIN","status":"ACTIVE"}
                """.formatted(email, uniqueUsername(), VALID_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        assertThat(accountCount(email)).isZero();
    }

    @Test
    void sinirAsanGovde413IleReddedilir() throws Exception {
        String email = uniqueEmail();

        register(email, uniqueUsername(), "a".repeat(9_000))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));

        assertThat(accountCount(email)).isZero();
    }

    @Test
    void eszamanliAyniKayitlardanYalnizBirHesapOlusur() throws Exception {
        String email = uniqueEmail();
        String username = uniqueUsername();

        List<Integer> statuses = ConcurrentRequests.run(CONCURRENT_REQUESTS,
                index -> () -> register(email, username, VALID_PASSWORD).andReturn().getResponse().getStatus());

        // Yarışta DB hangi tekillik ihlalini raporlarsa raporlasın yalnız 202 (e-posta) veya 409 (kullanıcı adı) döner.
        assertThat(statuses).allMatch(status -> status == 202 || status == 409).contains(202);
        assertThat(accountCount(email)).isEqualTo(1);
    }

    @Test
    void eszamanliAyniEPostaFarkliKullaniciAdlarindanYalnizBirHesapOlusur() throws Exception {
        String email = uniqueEmail();

        List<Integer> statuses = ConcurrentRequests.run(CONCURRENT_REQUESTS,
                index -> () -> register(email, uniqueUsername(), VALID_PASSWORD).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(202);
        assertThat(accountCount(email)).isEqualTo(1);
    }

    @Test
    void kayitUcuYalnizPostKabulEder() throws Exception {
        mockMvc.perform(get(AuthApiPaths.REGISTER)).andExpect(status().isUnauthorized());
    }

    @Test
    void bozukJson400Doner() throws Exception {
        mockMvc.perform(post(AuthApiPaths.REGISTER).contentType(MediaType.APPLICATION_JSON).content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
