package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.application.port.SecurityNotificationMail;
import com.ltz.auth.application.port.SecurityNotificationType;
import com.ltz.auth.domain.enums.AccountStatus;

/**
 * MAIL-03 ve MAIL-04: parola değişti, parola sıfırlandı, e-posta değişti (ESKİ adrese) ve hesap durumu bildirimleri.
 * Bildirim commit sonrası gider; başarısız işlem bildirim üretmez; parola, token ve bağlantı taşınmaz.
 */
@Import(RecordingMailPortConfiguration.class)
class SecurityNotificationTest extends IntegrationTestSupport {

    private static final String NEW_PASSWORD = "yeni-parola-yeni-parola-1";

    @Autowired
    private RecordingMailPort mailPort;

    @Autowired
    private AccountAdministrationService administration;

    @Test
    void parolaDegisinceBildirimGiderYanlisParolaBildirimUretmez() throws Exception {
        UUID account = newActive();
        String email = emailOf(account);
        String bearer = bearerFor(account);

        changePassword(bearer, "yanlis-parola-yanlis-parola", NEW_PASSWORD).andExpect(status().isBadRequest());
        changePassword(bearer, VALID_PASSWORD, NEW_PASSWORD).andExpect(status().is2xxSuccessful());

        SecurityNotificationMail mail = mailPort.awaitNotification(email, SecurityNotificationType.PASSWORD_CHANGED);
        assertThat(mail.accountId()).isEqualTo(account);
        assertThat(mail.idempotencyKey()).isNotNull();
        assertThat(mail.toString()).doesNotContain(email).doesNotContain(NEW_PASSWORD);
        assertThat(mailPort.notificationsTo(email)).as("yalnız başarılı değişiklik için tek bildirim").hasSize(1);
    }

    @Test
    void parolaSifirlaninceBildirimGider() throws Exception {
        UUID account = newActive();
        String email = emailOf(account);

        postJson(AuthApiPaths.PASSWORD_FORGOT, "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isAccepted());
        String token = mailPort.awaitLatestResetToken(email, 1);
        postJson(AuthApiPaths.PASSWORD_RESET, "{\"token\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirmation\":\"%s\"}"
                .formatted(token, NEW_PASSWORD, NEW_PASSWORD)).andExpect(status().is2xxSuccessful());

        SecurityNotificationMail mail = mailPort.awaitNotification(email, SecurityNotificationType.PASSWORD_RESET);
        assertThat(mail.accountId()).isEqualTo(account);
    }

    @Test
    void epostaDegisinceBildirimESKIAdreseGiderYeniAdreseGitmez() throws Exception {
        UUID account = newActive();
        String oldEmail = emailOf(account);
        String newEmail = uniqueEmail();
        String bearer = bearerFor(account);
        mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"%s\",\"password\":\"%s\"}".formatted(newEmail, VALID_PASSWORD)))
                .andExpect(status().isAccepted());
        mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE_CONFIRM).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(mailPort.awaitLatestCode(newEmail, 1))))
                .andExpect(status().isNoContent());

        mailPort.awaitNotification(oldEmail, SecurityNotificationType.EMAIL_CHANGED);
        assertThat(mailPort.notificationsTo(newEmail)).isEmpty();
    }

    @Test
    void durumDegisikligiHerGeciseUygunBildirimiUretir() throws Exception {
        UUID account = newActive();
        String email = emailOf(account);
        UUID actor = newActive();

        administration.changeStatus(actor, account, AccountStatus.SUSPENDED, "test");
        mailPort.awaitNotification(email, SecurityNotificationType.ACCOUNT_SUSPENDED);
        administration.changeStatus(actor, account, AccountStatus.ACTIVE, "test");
        mailPort.awaitNotification(email, SecurityNotificationType.ACCOUNT_REACTIVATED);
        administration.changeStatus(actor, account, AccountStatus.BANNED, "test");
        mailPort.awaitNotification(email, SecurityNotificationType.ACCOUNT_BANNED);

        assertThat(mailPort.notificationsTo(email)).extracting(SecurityNotificationMail::type)
                .containsExactlyInAnyOrder(SecurityNotificationType.ACCOUNT_SUSPENDED,
                        SecurityNotificationType.ACCOUNT_REACTIVATED, SecurityNotificationType.ACCOUNT_BANNED);
    }

    private UUID newActive() throws Exception {
        return accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
    }

    private String emailOf(UUID account) {
        return jdbc.queryForObject("select email from accounts where id = ?", String.class, account);
    }

    private ResultActions changePassword(String bearer, String current, String replacement) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.PASSWORD_CHANGE).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirmation\":\"%s\"}"
                        .formatted(current, replacement, replacement)));
    }
}
