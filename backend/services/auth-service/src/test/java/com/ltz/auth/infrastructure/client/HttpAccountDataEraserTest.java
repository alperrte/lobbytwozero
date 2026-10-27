package com.ltz.auth.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
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
import com.ltz.auth.application.port.AccountErasureException;
import com.ltz.auth.infrastructure.config.DeletionProperties;

class HttpAccountDataEraserTest {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final ErasureTargetStub TARGET = ErasureTargetStub.start();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    private final HttpAccountDataEraser eraser = eraser(Map.of("social", URI.create(TARGET.baseUrl())));

    @BeforeEach
    void reset() {
        TARGET.reset();
    }

    @AfterAll
    static void stop() {
        TARGET.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204})
    void yalniz200Ve204SilmeOnayidir(int status) {
        TARGET.respondWith(status);
        UUID accountId = UUID.randomUUID();

        assertThatCode(() -> eraser.erase("social", REQUEST_ID, accountId)).doesNotThrowAnyException();

        assertThat(TARGET.received()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("DELETE");
            assertThat(request.path()).isEqualTo("/internal/v1/accounts/" + accountId);
            ServiceTokenContract.verify(ServiceTokenContract.bearerToken(request.authorization()),
                    TestServiceTokens.signingKey().publicKey(), TestServiceTokens.ISSUER, "social-service",
                    accountId, CLOCK);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {202, 400, 401, 403, 404, 409, 500, 503})
    void digerKodlarOnaySayilmaz(int status) {
        TARGET.respondWith(status);
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> eraser.erase("social", REQUEST_ID, accountId))
                .isInstanceOf(AccountErasureException.class)
                .extracting(exception -> ((AccountErasureException) exception).code())
                .isEqualTo("SERVICE_ERASURE_HTTP_" + status);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 307, 308})
    void yonlendirmeIzlenmezVeOnaySayilmaz(int status) {
        TARGET.respondWith(request -> ErasureTargetStub.REDIRECT_PATH.equals(request.path()) ? 204 : status);
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> eraser.erase("social", REQUEST_ID, accountId))
                .isInstanceOf(AccountErasureException.class)
                .hasMessage("SERVICE_ERASURE_HTTP_" + status);
        assertThat(TARGET.received()).as("token yönlendirilen adrese gönderilmez").hasSize(1);
    }

    @Test
    void zamanAsimiOnaySayilmaz() {
        TARGET.delay(TIMEOUT.multipliedBy(3));
        TARGET.respondWith(204);
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> eraser.erase("social", REQUEST_ID, accountId))
                .isInstanceOf(AccountErasureException.class)
                .hasMessage("SERVICE_ERASURE_UNREACHABLE");
    }

    @Test
    void ulasilamayanServisOnaySayilmaz() {
        String closedUrl;
        try (ErasureTargetStub stopped = ErasureTargetStub.start()) {
            closedUrl = stopped.baseUrl();
        }
        HttpAccountDataEraser unreachable = eraser(Map.of("social", URI.create(closedUrl)));
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> unreachable.erase("social", REQUEST_ID, accountId))
                .isInstanceOf(AccountErasureException.class)
                .hasMessage("SERVICE_ERASURE_UNREACHABLE");
    }

    @Test
    void adresiTanimsizServisIcinIstekYapilmaz() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> eraser.erase("game", REQUEST_ID, accountId))
                .isInstanceOf(AccountErasureException.class)
                .hasMessage("SERVICE_ERASURE_NOT_CONFIGURED");
        assertThat(TARGET.received()).isEmpty();
    }

    @Test
    void herCagriAyriTokenKullanir() {
        TARGET.respondWith(204);
        UUID accountId = UUID.randomUUID();

        eraser.erase("social", REQUEST_ID, accountId);
        eraser.erase("social", REQUEST_ID, accountId);

        assertThat(TARGET.received()).extracting(ErasureTargetStub.Received::authorization).doesNotHaveDuplicates();
    }

    @Test
    void kapatildiktanSonraIstemciYenidenKullanilamazIkinciKapatmaGuvenlidir() {
        HttpAccountDataEraser closed = eraser(Map.of("social", URI.create(TARGET.baseUrl())));
        TARGET.respondWith(204);

        closed.close();

        assertThatCode(closed::close).doesNotThrowAnyException();
        assertThatThrownBy(() -> closed.erase("social", REQUEST_ID, UUID.randomUUID()))
                .isInstanceOf(AccountErasureException.class);
        assertThat(TARGET.received()).isEmpty();
    }

    private static HttpAccountDataEraser eraser(Map<String, URI> serviceUrls) {
        DeletionProperties properties = new DeletionProperties(Set.copyOf(serviceUrls.keySet()), serviceUrls, TIMEOUT,
                Duration.ofMinutes(30), Duration.ofSeconds(60), Duration.ofMinutes(10),
                URI.create("http://localhost:5001/account/delete"), false, Duration.ofSeconds(30), 20,
                Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofHours(6), 10, Duration.ofDays(14));
        return new HttpAccountDataEraser(properties, TestServiceTokens.issuer(CLOCK));
    }
}
