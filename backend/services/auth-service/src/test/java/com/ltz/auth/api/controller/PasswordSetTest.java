package com.ltz.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;
import com.ltz.auth.application.port.SecurityNotificationType;

/**
 * Parolasız (Google/Steam) hesabın ilk parolasını belirlemesi (B11): oturum + hesabın doğrulanmış adresine giden kod,
 * TOTP etkinse ikinci faktör, tek kullanımlık ve deneme sınırlı kod, parola politikası, başarıda oturumların
 * kapanması ve güvenlik bildirimi.
 */
@Import(RecordingMailPortConfiguration.class)
class PasswordSetTest extends IntegrationTestSupport {

    private static final String NEW_PASSWORD = "New-Secret-Passphrase-2026";
    private static final int MAX_CODE_ATTEMPTS = 5;

    @Autowired
    private RecordingMailPort mailPort;

    @Test
    void kodHesabinKendiAdresineGiderParolaBelirlenirOturumlarKapanirBildirimGider() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);
        // Oturum, hesap parolasız hâle gelmeden önce gerçek girişle açılır (Google/Steam oturumunu taklit eder).
        SessionCookiePair session = loginSession(email);
        jdbc.update("update accounts set password_hash = null where id = ?", account);

        requestCode(account, "{}").andExpect(status().isAccepted()).andExpect(jsonPath("$.nextStep").value("VERIFY_EMAIL"));
        String code = mailPort.awaitLatestCode(email, 1);
        assertThat(code).matches("^[0-9]{6}$");
        assertThat(passwordHashOf(account)).as("onaydan önce parola yok").isNull();

        setPassword(account, code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(passwordHashOf(account)).isNotNull();
        assertThat(securityEventCount(account, "PASSWORD_SET")).isEqualTo(1);
        assertThat(mailPort.awaitNotification(email, SecurityNotificationType.PASSWORD_CHANGED)).isNotNull();
        refresh(session.refresh()).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));

        // Kod tek kullanımlıktır ve hesap artık parolalıdır.
        setPassword(account, code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_ALREADY_SET"));
    }

    @Test
    void parolaliHesapBuYoluKullanamaz() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID account = accountId(email);

        requestCode(account, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_ALREADY_SET"));
        setPassword(account, "123456", NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_ALREADY_SET"));
        assertThat(mailPort.deliveredTo(email)).as("yalnız kayıt kodu gitti").hasSize(1);
    }

    @Test
    void yanlisKodDenemeleriSinirlidirHakBitinceDogruKodDaReddedilir() throws Exception {
        String email = passwordlessAccount();
        UUID account = accountId(email);
        requestCode(account, "{}").andExpect(status().isAccepted());
        String code = mailPort.awaitLatestCode(email, 1);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            setPassword(account, wrong, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_SET_INVALID"));
        }
        setPassword(account, code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_SET_INVALID"));
        assertThat(passwordHashOf(account)).isNull();
    }

    @Test
    void zayifParolaVeUyusmayanTekrarReddedilirKodYanmaz() throws Exception {
        String email = passwordlessAccount();
        UUID account = accountId(email);
        requestCode(account, "{}").andExpect(status().isAccepted());
        String code = mailPort.awaitLatestCode(email, 1);

        setPassword(account, code, "password1234567", "password1234567").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        setPassword(account, code, NEW_PASSWORD, NEW_PASSWORD + "x").andExpect(status().isBadRequest());
        assertThat(passwordHashOf(account)).isNull();

        // Kod hâlâ geçerli: güçlü parolayla tamamlanır.
        setPassword(account, code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(passwordHashOf(account)).isNotNull();
    }

    @Test
    void totpEtkinseIkinciFaktorOlmadanKodGonderilmez() throws Exception {
        String email = passwordlessAccount();
        UUID account = accountId(email);
        enableTotp(account);

        requestCode(account, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));
        assertThat(mailPort.deliveredTo(email)).as("yalnız kayıt kodu gitti").hasSize(1);
        assertThat(securityEventCount(account, "PASSWORD_SET_REJECTED")).isEqualTo(1);

        requestCode(account, "{\"code\":\"%s\"}".formatted(currentTotpCode(account))).andExpect(status().isAccepted());
        String code = mailPort.awaitLatestCode(email, 2);
        setPassword(account, code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void kimliksizIstekReddedilir() throws Exception {
        mockMvc.perform(post(AuthApiPaths.PASSWORD_SET_REQUEST).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post(AuthApiPaths.PASSWORD_SET).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailCode\":\"123456\",\"newPassword\":\"%s\",\"newPasswordConfirmation\":\"%s\"}"
                                .formatted(NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().is4xxClientError());
    }

    // ---- Yardımcılar ----

    /** Google ile açılmış hesabı taklit eder: ACTIVE, doğrulanmış e-posta, parola yok. */
    private String passwordlessAccount() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        jdbc.update("update accounts set password_hash = null where email_normalized = lower(?)", email);
        return email;
    }

    private ResultActions requestCode(UUID account, String body) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.PASSWORD_SET_REQUEST)
                .header(HttpHeaders.AUTHORIZATION, bearerFor(account))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions setPassword(UUID account, String code, String password, String confirmation) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.PASSWORD_SET).header(HttpHeaders.AUTHORIZATION, bearerFor(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailCode\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirmation\":\"%s\"}"
                        .formatted(code, password, confirmation)));
    }

    private String passwordHashOf(UUID account) {
        return jdbc.queryForObject("select password_hash from accounts where id = ?", String.class, account);
    }
}
