package com.ltz.mail.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.mail.IntegrationTestSupport;
import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.port.OutgoingMail;
import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.domain.enums.MailType;

class MailDispatchServiceTest extends IntegrationTestSupport {

    private static final String EMAIL = "kerem@example.com";

    @Autowired
    private MailEnqueueService enqueue;

    @Autowired
    private MailDispatchService dispatch;

    private UUID enqueueCode(UUID account, MailLocale locale) {
        UUID key = UUID.randomUUID();
        enqueue.enqueue(new MailCommand(key, account, MailType.EMAIL_VERIFICATION, EMAIL, locale,
                new MailPayload("123456", null, 600, null)));
        return key;
    }

    private UUID enqueueNotification(UUID account) {
        UUID key = UUID.randomUUID();
        enqueue.enqueue(new MailCommand(key, account, MailType.PASSWORD_CHANGED, EMAIL, MailLocale.TR,
                new MailPayload(null, null, null, Instant.now())));
        return key;
    }

    private int attemptCount(UUID messageId) {
        Integer count = jdbc.queryForObject("select count(*) from mail_delivery_attempts where message_id = ?",
                Integer.class, messageId);
        return count == null ? 0 : count;
    }

    @Test
    void sendsBilingualMailAndClearsSensitiveData() {
        UUID key = enqueueCode(newAccount(), null);

        assertThat(dispatch.dispatchDue()).isEqualTo(1);

        assertThat(mailSender.sent()).hasSize(1);
        OutgoingMail mail = mailSender.sent().getFirst();
        assertThat(mail.to()).isEqualTo(EMAIL);
        assertThat(mail.subject()).isEqualTo("LTZ e-posta doğrulama kodunuz / Your LTZ email verification code");
        assertThat(mail.html()).contains("123456", "cid:ltz-logo", "Verify your email address",
                "E-posta adresinizi doğrulayın").doesNotContain("{{");
        assertThat(mail.text()).contains("123456").doesNotContain("{{");

        Map<String, Object> row = messageByKey(key);
        assertThat(row).containsEntry("status", "SENT").containsEntry("attempts", 1)
                .containsEntry("recipient_masked", "k***@e***.com");
        assertThat(row.get("recipient_email")).isNull();
        assertThat(row.get("payload_encrypted")).isNull();
        assertThat(row.get("sent_at")).isNotNull();
        UUID id = (UUID) row.get("id");
        assertThat(attemptCount(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select result from mail_delivery_attempts where message_id = ?", String.class, id))
                .isEqualTo("SENT");
    }

    @Test
    void singleLanguageMailUsesOnlyThatLanguage() {
        enqueueCode(newAccount(), MailLocale.TR);

        dispatch.dispatchDue();

        OutgoingMail mail = mailSender.sent().getFirst();
        assertThat(mail.subject()).isEqualTo("LTZ e-posta doğrulama kodunuz");
        assertThat(mail.html()).doesNotContain("Verify your email address");
    }

    @Test
    void transientFailureIsRetriedLaterWithBackoff() {
        UUID key = enqueueCode(newAccount(), MailLocale.EN);
        mailSender.failNext(MailDeliveryException.transientFailure("SMTP_421", 421));

        dispatch.dispatchDue();

        Map<String, Object> row = messageByKey(key);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1)
                .containsEntry("last_error_code", "SMTP_421");
        assertThat(((Timestamp) row.get("next_attempt_at")).toInstant()).isAfter(Instant.now());
        assertThat(row.get("recipient_email")).isEqualTo(EMAIL);
        assertThat(dispatch.dispatchDue()).isZero();

        makeDue(key);
        assertThat(dispatch.dispatchDue()).isEqualTo(1);
        assertThat(messageByKey(key)).containsEntry("status", "SENT").containsEntry("attempts", 2);
        assertThat(attemptCount((UUID) row.get("id"))).isEqualTo(2);
    }

    @Test
    void permanentFailureStopsImmediatelyAndClearsData() {
        UUID key = enqueueCode(newAccount(), MailLocale.TR);
        mailSender.failNext(MailDeliveryException.permanent("SMTP_550", 550));

        dispatch.dispatchDue();

        Map<String, Object> row = messageByKey(key);
        assertThat(row).containsEntry("status", "FAILED").containsEntry("last_error_code", "SMTP_550");
        assertThat(row.get("recipient_email")).isNull();
        assertThat(row.get("payload_encrypted")).isNull();
        assertThat(jdbc.queryForObject("select smtp_code from mail_delivery_attempts where message_id = ?",
                Integer.class, row.get("id"))).isEqualTo(550);
    }

    @Test
    void failsAfterTheAttemptLimit() {
        UUID key = enqueueNotification(newAccount());

        for (int round = 0; round < 8; round++) {
            mailSender.failNext(MailDeliveryException.transientFailure("SMTP_421", 421));
            makeDue(key);
            assertThat(dispatch.dispatchDue()).isEqualTo(1);
        }

        Map<String, Object> row = messageByKey(key);
        assertThat(row).containsEntry("status", "FAILED").containsEntry("attempts", 8)
                .containsEntry("last_error_code", "RETRIES_EXHAUSTED");
        assertThat(attemptCount((UUID) row.get("id"))).isEqualTo(8);
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    void expiredMailIsNeverSent() {
        UUID key = enqueueCode(newAccount(), MailLocale.TR);
        jdbc.update("update mail_messages set expires_at = now() - interval '1 second' where idempotency_key = ?", key);

        dispatch.dispatchDue();

        assertThat(mailSender.sent()).isEmpty();
        Map<String, Object> row = messageByKey(key);
        assertThat(row).containsEntry("status", "FAILED").containsEntry("last_error_code", "EXPIRED");
        assertThat(jdbc.queryForObject("select result from mail_delivery_attempts where message_id = ?", String.class,
                row.get("id"))).isEqualTo("EXPIRED");
    }

    @Test
    void abandonedProcessingMailIsReclaimed() {
        UUID key = enqueueCode(newAccount(), MailLocale.TR);
        jdbc.update("""
                update mail_messages set status = 'PROCESSING', attempts = 1, lease_until = now() - interval '1 minute'
                where idempotency_key = ?""", key);

        assertThat(dispatch.dispatchDue()).isEqualTo(1);

        assertThat(messageByKey(key)).containsEntry("status", "SENT").containsEntry("attempts", 2);
    }

    @Test
    void mailWithActiveLeaseIsLeftAlone() {
        UUID key = enqueueCode(newAccount(), MailLocale.TR);
        jdbc.update("""
                update mail_messages set status = 'PROCESSING', attempts = 1, lease_until = now() + interval '5 minutes'
                where idempotency_key = ?""", key);

        assertThat(dispatch.dispatchDue()).isZero();
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    void unreadablePayloadFailsPermanently() {
        UUID key = enqueueCode(newAccount(), MailLocale.TR);
        jdbc.update("update mail_messages set payload_encrypted = decode('00112233445566778899aabbccddeeff00', 'hex') "
                + "where idempotency_key = ?", key);

        dispatch.dispatchDue();

        assertThat(messageByKey(key)).containsEntry("status", "FAILED").containsEntry("last_error_code",
                "PAYLOAD_UNREADABLE");
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    void payloadMovedToAnotherRecordCannotBeDecrypted() {
        UUID first = enqueueCode(newAccount(), MailLocale.TR);
        UUID second = enqueueCode(newAccount(), MailLocale.TR);
        jdbc.update("""
                update mail_messages set payload_encrypted =
                    (select payload_encrypted from mail_messages where idempotency_key = ?)
                where idempotency_key = ?""", first, second);

        dispatch.dispatchDue();

        assertThat(messageByKey(second)).containsEntry("status", "FAILED").containsEntry("last_error_code",
                "PAYLOAD_UNREADABLE");
        assertThat(messageByKey(first)).containsEntry("status", "SENT");
    }

    @Test
    void concurrentWorkersNeverSendTheSameMailTwice() throws Exception {
        List<UUID> keys = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            keys.add(enqueueCode(newAccount(), MailLocale.TR));
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            List<Callable<Integer>> rounds = List.of(dispatch::dispatchDue, dispatch::dispatchDue, dispatch::dispatchDue);
            int processed = 0;
            for (Future<Integer> result : pool.invokeAll(rounds)) {
                processed += result.get();
            }
            assertThat(processed).isEqualTo(6);
        }

        assertThat(mailSender.sent()).hasSize(6);
        for (UUID key : keys) {
            assertThat(messageByKey(key)).containsEntry("status", "SENT").containsEntry("attempts", 1);
        }
    }

    @Test
    void doesNothingWhenQueueIsEmpty() {
        assertThat(dispatch.dispatchDue()).isZero();
    }
}
