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

@Import(RecordingMailPortConfiguration.class)
class EmailChangeTest extends IntegrationTestSupport {

    private static final int MAX_CODE_ATTEMPTS = 5;

    @Autowired
    private RecordingMailPort mailPort;

    @Test
    void kodYeniAdreseGiderAdresOnaylaninceDegisirOturumlarKapanir() throws Exception {
        String oldEmail = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(oldEmail);
        SessionCookiePair session = loginSession(oldEmail);
        String newEmail = uniqueEmail();

        change(accountId, newEmail, VALID_PASSWORD).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.nextStep").value("VERIFY_EMAIL"));

        String code = mailPort.awaitLatestCode(newEmail, 1);
        assertThat(code).matches("^[0-9]{6}$");
        assertThat(mailPort.deliveredTo(oldEmail)).as("eski adrese kod gitmez").hasSize(1);
        assertThat(emailOf(accountId)).as("onaydan önce adres değişmez").isEqualToIgnoringCase(oldEmail);

        confirm(accountId, code).andExpect(status().isNoContent());

        assertThat(emailOf(accountId)).isEqualToIgnoringCase(newEmail);
        assertThat(securityEventCount(accountId, "EMAIL_CHANGED")).isEqualTo(1);
        refresh(session.refresh()).andExpect(status().isUnauthorized());
        login(oldEmail, VALID_PASSWORD).andExpect(status().isUnauthorized());
        login(newEmail, VALID_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
        // Kod tek kullanımlıktır.
        confirm(accountId, code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_INVALID"));
    }

    @Test
    void yanlisParolaReddedilirKodGonderilmez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        String newEmail = uniqueEmail();

        change(accountId(email), newEmail, "definitely-not-the-password").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

        assertThat(mailPort.deliveredTo(newEmail)).isEmpty();
        assertThat(securityEventCount(accountId(email), "EMAIL_CHANGE_REJECTED")).isEqualTo(1);
    }

    @Test
    void baskaHesabinAdresiIcinYanitAyniKalirKodGonderilmez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        String taken = registerActiveAccount(uniqueEmail(), uniqueUsername());
        String free = uniqueEmail();

        String freeBody = change(accountId(email), free, VALID_PASSWORD).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String takenBody = change(accountId(email), taken, VALID_PASSWORD).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(takenBody).isEqualTo(freeBody);
        assertThat(mailPort.deliveredTo(taken)).as("yalnız kayıt kodu gitti").hasSize(1);
        assertThat(securityEventCount(accountId(email), "EMAIL_CHANGE_CONFLICT")).isEqualTo(1);
    }

    @Test
    void yanlisKodDenemeleriSinirlidirHakBitinceDogruKodDaReddedilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        String newEmail = uniqueEmail();
        change(accountId, newEmail, VALID_PASSWORD).andExpect(status().isAccepted());
        String code = mailPort.awaitLatestCode(newEmail, 1);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            confirm(accountId, wrong).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_INVALID"));
        }
        confirm(accountId, code).andExpect(status().isBadRequest());

        assertThat(emailOf(accountId)).isEqualToIgnoringCase(email);
    }

    @Test
    void adresOnayaKadarBaskasinaGectiyseDegisimReddedilir() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        String newEmail = uniqueEmail();
        change(accountId, newEmail, VALID_PASSWORD).andExpect(status().isAccepted());
        String code = mailPort.awaitLatestCode(newEmail, 1);
        registerActiveAccount(newEmail, uniqueUsername());

        confirm(accountId, code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_INVALID"));

        assertThat(emailOf(accountId)).isEqualToIgnoringCase(email);
    }

    @Test
    void parolasizHesapAdresDegistiremez() throws Exception {
        String email = registerActiveAccount(uniqueEmail(), uniqueUsername());
        UUID accountId = accountId(email);
        jdbc.update("update accounts set password_hash = null where id = ?", accountId);

        change(accountId, uniqueEmail(), VALID_PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));
    }

    @Test
    void kimliksizIstekler401() throws Exception {
        mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"x@example.test\",\"password\":\"%s\"}".formatted(VALID_PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE_CONFIRM).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}")).andExpect(status().isUnauthorized());
    }

    private ResultActions change(UUID accountId, String newEmail, String password) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE).header(HttpHeaders.AUTHORIZATION, bearerFor(accountId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"%s\",\"password\":\"%s\"}".formatted(newEmail, password)));
    }

    private ResultActions confirm(UUID accountId, String code) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE_CONFIRM)
                .header(HttpHeaders.AUTHORIZATION, bearerFor(accountId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"%s\"}".formatted(code)));
    }

    private String emailOf(UUID accountId) {
        return jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId);
    }
}
