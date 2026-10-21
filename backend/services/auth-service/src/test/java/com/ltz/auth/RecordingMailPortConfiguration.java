package com.ltz.auth;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ltz.auth.application.port.AccountDeletionMail;
import com.ltz.auth.application.port.AccountDeletionMailPort;
import com.ltz.auth.application.port.PasswordResetMail;
import com.ltz.auth.application.port.PasswordResetMailPort;
import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.application.port.VerificationCodeMailPort;

/**
 * Yalnız test kapsamında: Mail Service'in teslimi kabul ettiği durumu temsil eden test adaptörü.
 * Üretimdeki fail-closed adaptörün yerini sadece bu konfigürasyonu içe aktaran testlerde alır.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingMailPortConfiguration {

    @Bean
    @Primary
    RecordingMailPort recordingMailPort() {
        return new RecordingMailPort();
    }

    public static class RecordingMailPort implements VerificationCodeMailPort, PasswordResetMailPort,
            AccountDeletionMailPort {

        private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);
        private static final String TOKEN_MARKER = "#token=";

        private final Queue<VerificationCodeMail> delivered = new ConcurrentLinkedQueue<>();
        private final Queue<PasswordResetMail> resets = new ConcurrentLinkedQueue<>();
        private final Queue<AccountDeletionMail> deletions = new ConcurrentLinkedQueue<>();

        @Override
        public void sendVerificationCode(VerificationCodeMail mail) {
            delivered.add(mail);
        }

        @Override
        public void sendPasswordReset(PasswordResetMail mail) {
            resets.add(mail);
        }

        @Override
        public void sendAccountDeletionConfirmation(AccountDeletionMail mail) {
            deletions.add(mail);
        }

        public List<AccountDeletionMail> deletionsTo(String email) {
            return deletions.stream().filter(mail -> mail.recipientEmail().equalsIgnoreCase(email)).toList();
        }

        public String awaitLatestDeletionToken(String email, int expectedDeliveries) {
            await().atMost(DELIVERY_TIMEOUT).until(() -> deletionsTo(email).size() >= expectedDeliveries);
            String link = deletionsTo(email).getLast().confirmationLink();
            return link.substring(link.indexOf(TOKEN_MARKER) + TOKEN_MARKER.length());
        }

        public List<VerificationCodeMail> deliveredTo(String email) {
            return delivered.stream().filter(mail -> mail.recipientEmail().equalsIgnoreCase(email)).toList();
        }

        public List<PasswordResetMail> resetsTo(String email) {
            return resets.stream().filter(mail -> mail.recipientEmail().equalsIgnoreCase(email)).toList();
        }

        public String awaitLatestCode(String email, int expectedDeliveries) {
            await().atMost(DELIVERY_TIMEOUT).until(() -> deliveredTo(email).size() >= expectedDeliveries);
            return deliveredTo(email).getLast().code();
        }

        /** E-postadaki bağlantının fragment'inden token'ı çıkarır. */
        public String awaitLatestResetToken(String email, int expectedDeliveries) {
            await().atMost(DELIVERY_TIMEOUT).until(() -> resetsTo(email).size() >= expectedDeliveries);
            String link = resetsTo(email).getLast().resetLink();
            return link.substring(link.indexOf(TOKEN_MARKER) + TOKEN_MARKER.length());
        }
    }
}
