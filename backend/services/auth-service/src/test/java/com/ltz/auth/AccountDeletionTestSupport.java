package com.ltz.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;
import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.application.service.AccountDeletionCoordinator;

/** Hesap silme testleri için ortak yardımcılar. Alt sınıflar RecordingMailPortConfiguration'ı içe aktarmalıdır. */
public abstract class AccountDeletionTestSupport extends IntegrationTestSupport {

    @Autowired
    protected RecordingMailPort mailPort;

    @Autowired
    protected AccountDeletionCoordinator coordinator;

    /** Paylaşılan test veritabanındaki önceki testlerin bekleyen görevleri bu testin sayımlarını etkilemesin. */
    @BeforeEach
    void postponeForeignTasks() {
        jdbc.update("update account_deletion_tasks set next_attempt_at = now() + interval '1 day' "
                + "where status = 'PENDING'");
    }

    protected ResultActions requestDeletion(String bearer, String json) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.ACCOUNT_DELETION_REQUEST).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions confirmDeletion(String bearer, String token, String username) throws Exception {
        return mockMvc.perform(post(AuthApiPaths.ACCOUNT_DELETION_CONFIRM).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"username\":\"%s\"}".formatted(token, username)));
    }

    protected String passwordJson() {
        return "{\"password\":\"%s\"}".formatted(VALID_PASSWORD);
    }

    protected String usernameOf(UUID accountId) {
        return jdbc.queryForObject("select username from accounts where id = ?", String.class, accountId);
    }

    /** Parolalı hesap için tam akış: yeniden doğrulama → e-posta token'ı → kullanıcı adıyla onay. */
    protected UUID deleteAccountConfirmed(String email) throws Exception {
        UUID accountId = accountId(email);
        String bearer = bearerFor(accountId);
        requestDeletion(bearer, passwordJson()).andExpect(status().isAccepted());
        confirmDeletion(bearer, mailPort.awaitLatestDeletionToken(email, 1), usernameOf(accountId))
                .andExpect(status().isAccepted());
        return accountId;
    }

    protected int pendingTasks(UUID accountId) {
        return jdbc.queryForObject("select count(*) from account_deletion_tasks where account_id = ? "
                + "and status = 'PENDING'", Integer.class, accountId);
    }
}
