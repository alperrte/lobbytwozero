package com.ltz.mail.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.ltz.mail.IntegrationTestSupport;

class AccountErasureTest extends IntegrationTestSupport {

    private static final String PATH = "/internal/v1/accounts/{accountId}";

    private void seed(UUID account, UUID key) {
        jdbc.update("""
                insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id, recipient_email,
                                           recipient_masked, payload_encrypted, payload_key_version, status, attempts,
                                           next_attempt_at, expires_at, created_at, updated_at, version)
                values (?, ?, 'h', 'PASSWORD_CHANGED', ?, 'kerem@example.com', 'k***@e***.com', decode('00', 'hex'), 1,
                        'PENDING', 0, now(), now() + interval '1 hour', now(), now(), 0)""",
                UUID.randomUUID(), key, account);
        UUID messageId = jdbc.queryForObject("select id from mail_messages where idempotency_key = ?", UUID.class, key);
        jdbc.update("""
                insert into mail_delivery_attempts (id, message_id, attempt_no, attempted_at, result, duration_ms)
                values (?, ?, 1, now(), 'TRANSIENT_FAILURE', 5)""", UUID.randomUUID(), messageId);
    }

    @Test
    void erasesQueueAndHistoryOfOnlyThatAccount() throws Exception {
        UUID account = newAccount();
        UUID other = newAccount();
        seed(account, UUID.randomUUID());
        seed(account, UUID.randomUUID());
        seed(other, UUID.randomUUID());

        mockMvc.perform(delete(PATH, account).header(HttpHeaders.AUTHORIZATION, bearer(account, ERASE_SCOPE)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from mail_messages where recipient_user_id = ?",
                Integer.class, account)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from mail_messages where recipient_user_id = ?",
                Integer.class, other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from mail_delivery_attempts", Integer.class)).isEqualTo(1);
    }

    @Test
    void isIdempotentAndSucceedsWithoutData() throws Exception {
        UUID account = newAccount();

        for (int index = 0; index < 2; index++) {
            mockMvc.perform(delete(PATH, account).header(HttpHeaders.AUTHORIZATION, bearer(account, ERASE_SCOPE)))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void rejectsWrongCallerScopeAccountAndTokenType() throws Exception {
        UUID account = newAccount();
        seed(account, UUID.randomUUID());

        mockMvc.perform(delete(PATH, account)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PATH, account).header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken(account)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PATH, account).header(HttpHeaders.AUTHORIZATION, bearer(account, MAIL_SEND_SCOPE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(PATH, account).header(HttpHeaders.AUTHORIZATION, bearer(newAccount(), ERASE_SCOPE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(PATH, "not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(account, ERASE_SCOPE)))
                .andExpect(status().isBadRequest());

        assertThat(messageCount()).isEqualTo(1);
    }
}
