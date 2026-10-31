package com.ltz.auth.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ltz.auth.ErasureTargetStub;
import com.ltz.auth.ServiceTokenContract;
import com.ltz.auth.TestServiceTokens;
import com.ltz.auth.application.port.AccountDeletionMail;
import com.ltz.auth.application.port.PasswordResetMail;
import com.ltz.auth.application.port.SecurityNotificationMail;
import com.ltz.auth.application.port.SecurityNotificationType;
import com.ltz.auth.application.port.VerificationCodeDeliveryException;
import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.infrastructure.config.DeletionProperties;
import com.ltz.auth.infrastructure.security.TokenClaims;

/** Auth → Mail Service sözleşmesi: yol, svc+jwt, gövde alanları, yalnız 202 kabul. */
class HttpMailServiceAdapterTest {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final ErasureTargetStub TARGET = ErasureTargetStub.start();
    private static final UUID KEY = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final String EMAIL = "kullanici@example.test";

    private final HttpMailServiceAdapter adapter = adapter(Map.of("mail", URI.create(TARGET.baseUrl())));

    @BeforeEach
    void reset() {
        TARGET.reset();
        TARGET.respondWith(202);
    }

    @AfterAll
    static void stop() {
        TARGET.close();
    }

    @Test
    void dogrulamaKoduDogruYolaTokenIleVeGovdeyleGider() {
        adapter.sendVerificationCode(new VerificationCodeMail(KEY, ACCOUNT, EMAIL, "123456", Duration.ofMinutes(10)));

        assertThat(TARGET.received()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/internal/v1/mail/verification-codes");
            assertThat(request.body()).contains("\"idempotencyKey\":\"" + KEY + "\"", "\"accountId\":\"" + ACCOUNT + "\"",
                    "\"recipientEmail\":\"" + EMAIL + "\"", "\"code\":\"123456\"", "\"validitySeconds\":600")
                    .doesNotContain("locale");
            ServiceTokenContract.verify(ServiceTokenContract.bearerToken(request.authorization()),
                    TestServiceTokens.signingKey().publicKey(), TestServiceTokens.ISSUER, "mail-service",
                    TokenClaims.MAIL_SEND_SCOPE, ACCOUNT, CLOCK);
        });
    }

    @Test
    void parolaSifirlamaBaglantisiGider() {
        adapter.sendPasswordReset(new PasswordResetMail(KEY, ACCOUNT, EMAIL, "https://ltz.example/reset?t=abc",
                Duration.ofMinutes(30)));

        assertThat(TARGET.received()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/internal/v1/mail/password-resets");
            assertThat(request.body()).contains("\"resetLink\":\"https://ltz.example/reset?t=abc\"",
                    "\"validitySeconds\":1800");
        });
    }

    @Test
    void hesapSilmeOnayBaglantisiGider() {
        adapter.sendAccountDeletionConfirmation(new AccountDeletionMail(KEY, ACCOUNT, EMAIL,
                "https://ltz.example/delete?t=abc", Duration.ofHours(1)));

        assertThat(TARGET.received()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/internal/v1/mail/account-deletions");
            assertThat(request.body()).contains("\"confirmationLink\":\"https://ltz.example/delete?t=abc\"",
                    "\"validitySeconds\":3600");
        });
    }

    @Test
    void guvenlikBildirimiTurVeZamanTasirIcerikTasimaz() {
        Instant occurredAt = Instant.parse("2026-10-31T12:00:00Z");

        adapter.sendSecurityNotification(new SecurityNotificationMail(KEY, ACCOUNT, EMAIL,
                SecurityNotificationType.PASSWORD_RESET, occurredAt));

        assertThat(TARGET.received()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/internal/v1/mail/security-notifications");
            assertThat(request.body()).contains("\"type\":\"PASSWORD_RESET\"", "\"occurredAt\":\"2026-10-31T12:00:00Z\"")
                    .doesNotContain("code", "Link", "validitySeconds");
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204, 400, 401, 403, 409, 413, 429, 500, 503})
    void yalnizHttp202KabulSayilir(int status) {
        TARGET.respondWith(status);

        assertThatThrownBy(() -> adapter.sendVerificationCode(
                new VerificationCodeMail(KEY, ACCOUNT, EMAIL, "123456", Duration.ofMinutes(10))))
                .isInstanceOf(VerificationCodeDeliveryException.class)
                .hasMessageContaining("HTTP " + status)
                .message().doesNotContain("123456", EMAIL);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307, 308})
    void yonlendirmeIzlenmezVeKabulSayilmaz(int status) {
        TARGET.respondWith(request -> ErasureTargetStub.REDIRECT_PATH.equals(request.path()) ? 202 : status);

        assertThatThrownBy(() -> adapter.sendSecurityNotification(new SecurityNotificationMail(KEY, ACCOUNT, EMAIL,
                SecurityNotificationType.PASSWORD_CHANGED, Instant.now())))
                .isInstanceOf(VerificationCodeDeliveryException.class);
        assertThat(TARGET.received()).as("token yönlendirilen adrese gitmez").hasSize(1);
    }

    @Test
    void zamanAsimiTeslimHatasidir() {
        TARGET.delay(TIMEOUT.multipliedBy(3));

        assertThatThrownBy(() -> adapter.sendPasswordReset(new PasswordResetMail(KEY, ACCOUNT, EMAIL,
                "https://ltz.example/reset?t=abc", Duration.ofMinutes(30))))
                .isInstanceOf(VerificationCodeDeliveryException.class)
                .hasMessage("Mail Service is unreachable");
    }

    @Test
    void ulasilamayanServisTeslimHatasidir() {
        String closedUrl;
        try (ErasureTargetStub stopped = ErasureTargetStub.start()) {
            closedUrl = stopped.baseUrl();
        }
        HttpMailServiceAdapter unreachable = adapter(Map.of("mail", URI.create(closedUrl)));

        assertThatThrownBy(() -> unreachable.sendVerificationCode(
                new VerificationCodeMail(KEY, ACCOUNT, EMAIL, "123456", Duration.ofMinutes(10))))
                .isInstanceOf(VerificationCodeDeliveryException.class)
                .hasMessage("Mail Service is unreachable");
    }

    @Test
    void herCagriAyriTokenKullanir() {
        VerificationCodeMail mail = new VerificationCodeMail(KEY, ACCOUNT, EMAIL, "123456", Duration.ofMinutes(10));

        adapter.sendVerificationCode(mail);
        adapter.sendVerificationCode(mail);

        assertThat(TARGET.received()).extracting(ErasureTargetStub.Received::authorization).doesNotHaveDuplicates();
    }

    @Test
    void mailHedefAdresiYoksaAdaptorOlusmaz() {
        assertThatThrownBy(() -> adapter(Map.of("social", URI.create(TARGET.baseUrl()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service-urls.mail");
    }

    private static HttpMailServiceAdapter adapter(Map<String, URI> serviceUrls) {
        DeletionProperties properties = new DeletionProperties(Set.copyOf(serviceUrls.keySet()), serviceUrls, TIMEOUT,
                Duration.ofMinutes(30), Duration.ofSeconds(60), Duration.ofMinutes(10),
                URI.create("http://localhost:5001/account/delete"), false, Duration.ofSeconds(30), 20,
                Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofHours(6), 10, Duration.ofDays(14));
        return new HttpMailServiceAdapter(properties, TestServiceTokens.issuer(CLOCK));
    }
}
