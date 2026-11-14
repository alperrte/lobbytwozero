package com.ltz.mail.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    void templatesLiveInFilesNotInTheDatabase() {
        assertThat(jdbc.queryForObject("select to_regclass('mail_templates') is null", Boolean.class)).isTrue();
    }

    @Test
    void everyMailTypeIsAllowedByTheTypeConstraint() {
        for (MailType type : MailType.values()) {
            assertThat(jdbc.update("""
                    insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id,
                                               recipient_email, recipient_masked, payload_key_version, payload_encrypted,
                                               status, attempts, next_attempt_at, expires_at, created_at, updated_at, version)
                    values (?, ?, 'h', ?, ?, 'a@b.com', 'a***@b***.com', 1, '\\x00'::bytea, 'PENDING', 0, now(), now(),
                            now(), now(), 0)""", UUID.randomUUID(), UUID.randomUUID(), type.name(), newAccount()))
                    .isEqualTo(1);
        }
    }

    @Test
    void pendingRowWithoutAnAddressIsOnlyAllowedForPlatformMessages() {
        String insert = """
                insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id, recipient_email,
                                           recipient_masked, caller, payload_encrypted, payload_key_version, status, attempts,
                                           next_attempt_at, expires_at, created_at, updated_at, version)
                values (?, ?, 'h', 'NOTICE', ?, null, '***', ?, '\\x00'::bytea, 1, 'PENDING', 0, now(), now(), now(), now(), 0)""";

        assertThat(jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), newAccount(), "social-service")).isEqualTo(1);
        UUID secondId = UUID.randomUUID();
        UUID secondKey = UUID.randomUUID();
        UUID secondAccount = newAccount();
        assertThatThrownBy(() -> jdbc.update(insert, secondId, secondKey, secondAccount, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Kısıtı ihlal eden satırlar: bitmiş satır hassas veri tutamaz, aktif satır alıcı ve yükünü korumalı, tür/durum bilinmeli. */
    static Stream<Arguments> rejectedInserts() {
        return Stream.of(
                Arguments.of("terminalRowsCannotKeepSensitiveData", """
                        insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id,
                                                   recipient_email, recipient_masked, payload_key_version, status,
                                                   attempts, next_attempt_at, expires_at, created_at, updated_at,
                                                   sent_at, version)
                        values (?, ?, 'h', 'PASSWORD_CHANGED', ?, 'kerem@example.com', 'k***@e***.com', 1, 'SENT', 1,
                                now(), now(), now(), now(), now(), 0)"""),
                Arguments.of("activeRowsMustKeepTheirRecipientAndPayload", """
                        insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id,
                                                   recipient_masked, payload_key_version, status, attempts,
                                                   next_attempt_at, expires_at, created_at, updated_at, version)
                        values (?, ?, 'h', 'PASSWORD_CHANGED', ?, 'k***@e***.com', 1, 'PENDING', 0, now(), now(),
                                now(), now(), 0)"""),
                Arguments.of("unknownTypeAndStatusAreRejected", """
                        insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id,
                                                   recipient_email, recipient_masked, payload_key_version,
                                                   payload_encrypted, status, attempts, next_attempt_at, expires_at,
                                                   created_at, updated_at, version)
                        values (?, ?, 'h', 'BOGUS', ?, 'a@b.com', 'a***@b***.com', 1, '\\x00'::bytea, 'PENDING', 0,
                                now(), now(), now(), now(), 0)"""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedInserts")
    void constraintViolatingRowsAreRejected(String scenario, String insert) {
        UUID id = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        UUID account = newAccount();

        assertThatThrownBy(() -> jdbc.update(insert, id, key, account))
                .as(scenario)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKeyIsUnique() {
        UUID key = insertTerminal("FAILED", 0);

        String insert = INSERT_TERMINAL.formatted("null");
        UUID id = UUID.randomUUID();
        UUID account = newAccount();

        assertThatThrownBy(() -> jdbc.update(insert, id, key, account, "FAILED", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownTypeAndStatusAreRejected() {
        String insert = """
                insert into mail_messages (id, idempotency_key, request_hash, type, recipient_user_id, recipient_email,
                                           recipient_masked, payload_key_version, payload_encrypted, status, attempts,
                                           next_attempt_at, expires_at, created_at, updated_at, version)
                values (?, ?, 'h', 'BOGUS', ?, 'a@b.com', 'a***@b***.com', 1, '\\x00'::bytea, 'PENDING', 0, now(), now(),
                        now(), now(), 0)""";
        UUID id = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        UUID account = newAccount();

        assertThatThrownBy(() -> jdbc.update(insert, id, key, account))
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
