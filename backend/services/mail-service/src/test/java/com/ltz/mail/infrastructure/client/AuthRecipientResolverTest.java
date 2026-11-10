package com.ltz.mail.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.port.RecipientResolverPort.AccountContact;
import com.ltz.mail.infrastructure.config.AuthClientProperties;
import com.sun.net.httpserver.HttpServer;

/** Auth taklit eden yerel HTTP sunucusuyla: token alma, önbellek, sonuç sınıflaması, gizli bilgi sızıntısı. */
class AuthRecipientResolverTest {

    private static final String SECRET = "unit-test-client-secret-0123456789-abcdef";
    private static final UUID ACCOUNT = UUID.randomUUID();

    private HttpServer server;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final AtomicInteger contactCalls = new AtomicInteger();
    private final AtomicInteger tokenStatus = new AtomicInteger(200);
    private final AtomicInteger contactStatus = new AtomicInteger(200);
    private final AtomicReference<String> contactBody = new AtomicReference<>();
    private final AtomicReference<String> lastTokenRequest = new AtomicReference<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-09T12:00:00Z"));
    private AuthRecipientResolver resolver;

    @BeforeEach
    void start() throws IOException {
        contactBody.set("{\"accountId\":\"" + ACCOUNT + "\",\"status\":\"ACTIVE\",\"email\":\"kerem@example.com\","
                + "\"emailVerified\":true,\"extra\":\"yok sayilir\"}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/service-tokens", exchange -> {
            tokenCalls.incrementAndGet();
            lastTokenRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, tokenStatus.get(), "{\"accessToken\":\"tok-" + tokenCalls.get()
                    + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"account:contact:read\"}");
        });
        server.createContext("/internal/v1/accounts/", exchange -> {
            contactCalls.incrementAndGet();
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, contactStatus.get(), contactBody.get());
        });
        server.start();
        resolver = resolver(SECRET);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AuthRecipientResolver resolver(String secret) {
        AuthClientProperties properties = new AuthClientProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                "mail-service", secret, "/internal/v1/service-tokens", "/internal/v1/accounts/{accountId}/contact");
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        return new AuthRecipientResolver(RestClient.builder().baseUrl(properties.baseUrl()).build(), clock, properties);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @Test
    void resolvesTheContactWithAFreshTokenAndIgnoresUnknownFields() {
        AccountContact contact = resolver.resolve(ACCOUNT);

        assertThat(contact.email()).isEqualTo("kerem@example.com");
        assertThat(contact.emailVerified()).isTrue();
        assertThat(contact.status()).isEqualTo("ACTIVE");
        assertThat(authorizations).containsExactly("Bearer tok-1");
        // Token isteği yalnız kimlik bilgilerini taşır; hedef (audience) verilmez.
        assertThat(lastTokenRequest.get()).contains("\"clientId\":\"mail-service\"").contains(SECRET)
                .doesNotContain("audience");
    }

    @Test
    void theTokenIsReusedUntilShortlyBeforeItExpiresThenRenewed() {
        resolver.resolve(ACCOUNT);
        resolver.resolve(ACCOUNT);
        assertThat(tokenCalls.get()).isEqualTo(1);

        now.set(now.get().plus(Duration.ofSeconds(50)));
        resolver.resolve(ACCOUNT);

        assertThat(tokenCalls.get()).isEqualTo(2);
        assertThat(authorizations).containsExactly("Bearer tok-1", "Bearer tok-1", "Bearer tok-2");
    }

    @Test
    void unknownAccountIsPermanent() {
        contactStatus.set(404);

        assertThatThrownBy(() -> resolver.resolve(ACCOUNT)).isInstanceOfSatisfying(MailDeliveryException.class, exception -> {
            assertThat(exception.isPermanent()).isTrue();
            assertThat(exception.errorCode()).isEqualTo("RECIPIENT_NOT_FOUND");
        });
    }

    @Test
    void rejectedTokenIsTransientAndForcesANewTokenNextTime() {
        resolver.resolve(ACCOUNT);
        contactStatus.set(401);

        assertThatThrownBy(() -> resolver.resolve(ACCOUNT)).isInstanceOfSatisfying(MailDeliveryException.class, exception -> {
            assertThat(exception.isPermanent()).isFalse();
            assertThat(exception.errorCode()).isEqualTo("RESOLVER_TOKEN_UNAVAILABLE");
        });
        contactStatus.set(200);
        resolver.resolve(ACCOUNT);

        assertThat(tokenCalls.get()).isEqualTo(2);
    }

    @Test
    void forbiddenServerErrorsAndOutagesAreAllTransient() {
        contactStatus.set(403);
        assertThatThrownBy(() -> resolver.resolve(ACCOUNT)).isInstanceOfSatisfying(MailDeliveryException.class, exception -> {
            assertThat(exception.isPermanent()).isFalse();
            assertThat(exception.errorCode()).isEqualTo("RESOLVER_FORBIDDEN");
        });
        contactStatus.set(500);
        assertThatThrownBy(() -> resolver.resolve(ACCOUNT)).isInstanceOfSatisfying(MailDeliveryException.class, exception -> {
            assertThat(exception.isPermanent()).isFalse();
            assertThat(exception.errorCode()).isEqualTo("RESOLVER_UNAVAILABLE");
        });
        server.stop(0);
        assertThatThrownBy(() -> resolver.resolve(ACCOUNT)).isInstanceOfSatisfying(MailDeliveryException.class,
                exception -> assertThat(exception.isPermanent()).isFalse());
    }

    @Test
    void tokenEndpointFailureIsTransientAndNeverLeaksTheSecret() {
        tokenStatus.set(401);

        assertThatThrownBy(() -> resolver.resolve(ACCOUNT)).isInstanceOfSatisfying(MailDeliveryException.class, exception -> {
            assertThat(exception.isPermanent()).isFalse();
            assertThat(exception.errorCode()).isEqualTo("RESOLVER_TOKEN_UNAVAILABLE");
            assertThat(String.valueOf(exception.getMessage())).doesNotContain(SECRET);
        });
        assertThat(contactCalls.get()).isZero();
    }

    @Test
    void missingSecretMeansNotConfiguredAndNoRequestIsSent() {
        AuthRecipientResolver unconfigured = resolver("");

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.resolve(ACCOUNT)).isInstanceOf(MailDeliveryException.class);
        assertThat(tokenCalls.get()).isZero();
    }

    @Test
    void accountWithoutAnAddressIsReturnedAsIsForTheCallerToDecide() {
        contactBody.set("{\"accountId\":\"" + ACCOUNT + "\",\"status\":\"ACTIVE\",\"email\":null,\"emailVerified\":false}");

        AccountContact contact = resolver.resolve(ACCOUNT);

        assertThat(contact.email()).isNull();
        assertThat(contact.emailVerified()).isFalse();
        assertThat(contact.toString()).doesNotContain("kerem");
    }

    @Test
    void propertiesNeverPrintTheSecret() {
        AuthClientProperties properties = new AuthClientProperties("http://auth", "mail-service", SECRET, "/t", "/c");

        assertThat(properties.toString()).doesNotContain(SECRET);
    }
}
