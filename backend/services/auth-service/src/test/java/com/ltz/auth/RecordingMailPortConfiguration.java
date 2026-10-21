package com.ltz.auth;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

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

    public static class RecordingMailPort implements VerificationCodeMailPort {

        private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);

        private final Queue<VerificationCodeMail> delivered = new ConcurrentLinkedQueue<>();

        @Override
        public void sendVerificationCode(VerificationCodeMail mail) {
            delivered.add(mail);
        }

        public List<VerificationCodeMail> deliveredTo(String email) {
            return delivered.stream().filter(mail -> mail.recipientEmail().equalsIgnoreCase(email)).toList();
        }

        public String awaitLatestCode(String email, int expectedDeliveries) {
            await().atMost(DELIVERY_TIMEOUT).until(() -> deliveredTo(email).size() >= expectedDeliveries);
            return deliveredTo(email).getLast().code();
        }
    }
}
