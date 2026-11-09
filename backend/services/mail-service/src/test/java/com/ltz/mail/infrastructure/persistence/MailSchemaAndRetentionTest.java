package com.ltz.mail.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.ltz.mail.IntegrationTestSupport;
import com.ltz.mail.application.service.MailRetentionService;
import com.ltz.mail.domain.enums.MailType;

class MailSchemaAndRetentionTest extends IntegrationTestSupport {

    private static final String INSERT_TERMINAL = """
            insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id, recipient_masked,
                                       payload_key_version, status, attempts, next_attempt_at, expires_at, created_at,
                                       updated_at, sent_at, version)
            values (?, ?, 'h', 'PASSWORD_CHANGED', ?, 'k***@e***.com', 1, ?, 1, now(), now(), now(),
                    now() - make_interval(days => ?), %s, 0)""";

    @Autowired
    private MailRetentionService retention;

    private UUID insertTerminal(String status, int ageDays) {
        UUID key = UUID.randomUUID();
        String sentAt = "SENT".equals(status) ? "now()" : "null";
        jdbc.update(INSERT_TERMINAL.formatted(sentAt), UUID.randomUUID(), key, newAccount(), status, ageDays);
        return key;
    }

    @Test
    void seedsActiveTemplatesForEveryTypeAndLanguage() {
        int types = MailType.values().length;
        assertThat(jdbc.queryForObject("select count(*) from mail_templates where active", Integer.class))
                .isEqualTo(types * 2);
        assertThat(jdbc.queryForObject("select count(distinct code) from mail_templates", Integer.class))
                .isEqualTo(types);
        assertThat(jdbc.queryForObject("select count(*) from mail_templates where locale = 'tr' and active",
                Integer.class)).isEqualTo(types);
    }

    @Test
    void onlyTheDarkRedesignVersionIsActiveAndOlderVersionsStayForRollback() {
        assertThat(jdbc.queryForObject("select count(*) from mail_templates where active and version <> 2",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from mail_templates where active and version = 1",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from mail_templates where not active and version = 1",
                Integer.class)).isPositive();
    }

    @Test
    void terminalRowsCannotKeepSensitiveData() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id, recipient_email,
                                           recipient_masked, payload_key_version, status, attempts, next_attempt_at,
                                           expires_at, created_at, updated_at, sent_at, version)
                values (?, ?, 'h', 'PASSWORD_CHANGED', ?, 'kerem@example.com', 'k***@e***.com', 1, 'SENT', 1, now(),
                        now(), now(), now(), now(), 0)""", UUID.randomUUID(), UUID.randomUUID(), newAccount()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeRowsMustKeepTheirRecipientAndPayload() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id, recipient_masked,
                                           payload_key_version, status, attempts, next_attempt_at, expires_at,
                                           created_at, updated_at, version)
                values (?, ?, 'h', 'PASSWORD_CHANGED', ?, 'k***@e***.com', 1, 'PENDING', 0, now(), now(), now(), now(), 0)""",
                UUID.randomUUID(), UUID.randomUUID(), newAccount()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKeyIsUnique() {
        UUID key = insertTerminal("FAILED", 0);

        assertThatThrownBy(() -> jdbc.update(INSERT_TERMINAL.formatted("null"), UUID.randomUUID(), key, newAccount(),
                "FAILED", 0)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownTypeAndStatusAreRejected() {
        assertThatThrownBy(() -> jdbc.update("update mail_templates set locale = 'de' where code = 'EMAIL_VERIFICATION'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void retentionRemovesOnlyOldTerminalRows() {
        UUID oldSent = insertTerminal("SENT", 100);
        UUID oldFailed = insertTerminal("FAILED", 100);
        UUID recentSent = insertTerminal("SENT", 1);

        int removed = retention.purge();

        assertThat(removed).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from mail_messages where idempotency_key in (?, ?)",
                Integer.class, oldSent, oldFailed)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from mail_messages where idempotency_key = ?",
                Integer.class, recentSent)).isEqualTo(1);
    }

    @Test
    void retentionKeepsOldRowsThatAreStillActive() {
        UUID key = UUID.randomUUID();
        jdbc.update("""
                insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id, recipient_email,
                                           recipient_masked, payload_encrypted, payload_key_version, status, attempts,
                                           next_attempt_at, expires_at, created_at, updated_at, version)
                values (?, ?, 'h', 'PASSWORD_CHANGED', ?, 'kerem@example.com', 'k***@e***.com', decode('00', 'hex'), 1,
                        'PENDING', 0, now(), now(), now(), now() - interval '200 days', 0)""",
                UUID.randomUUID(), key, newAccount());

        assertThat(retention.purge()).isZero();
        assertThat(messageCount()).isEqualTo(1);
    }
}
