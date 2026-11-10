package com.ltz.auth.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ltz.auth.ErasureTargetStub;
import com.ltz.auth.ServiceTokenContract;
import com.ltz.auth.TestServiceTokens;
import com.ltz.auth.infrastructure.config.DeletionProperties;
import com.ltz.auth.infrastructure.security.TokenClaims;

/** Auth → User locale ucu: yol, svc+jwt, en iyi çaba (404/5xx/zaman aşımı/geçersiz değer → boş), önbellek. */
class HttpUserLocaleClientTest {

    private static final ErasureTargetStub TARGET = ErasureTargetStub.start();
    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.parse("2026-11-10T10:00:00Z"));
    private static final Clock MUTABLE_CLOCK = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return NOW.get();
        }
    };

    private HttpUserLocaleClient client;

    @BeforeEach
    void reset() {
        TARGET.reset();
        NOW.set(Instant.parse("2026-11-10T10:00:00Z"));
        client = client(Map.of("user", URI.create(TARGET.baseUrl())));
    }

    @AfterAll
    static void stop() {
        TARGET.close();
    }

    @Test
    void tercihEnIkenEnDonerVeIstekSozlesmeyeUyar() {
        TARGET.respondWithJson("{\"locale\":\"en\"}");

        assertThat(client.localeOf(ACCOUNT)).contains("en");

        assertThat(TARGET.received()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/internal/v1/users/" + ACCOUNT + "/locale");
            ServiceTokenContract.verify(ServiceTokenContract.bearerToken(request.authorization()),
                    TestServiceTokens.signingKey().publicKey(), TestServiceTokens.ISSUER, "user-service",
                    TokenClaims.PREFERENCES_LOCALE_READ_SCOPE, ACCOUNT, Clock.systemUTC());
        });
    }

    @Test
    void tercihTrIkenTrDoner() {
        TARGET.respondWithJson("{\"locale\":\"tr\"}");

        assertThat(client.localeOf(ACCOUNT)).contains("tr");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    void hataDurumlarindaDilBilinmiyorSayilir(int status) {
        TARGET.respondWith(status);

        assertThat(client.localeOf(ACCOUNT)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"locale\":\"de\"}", "{\"locale\":\"EN\"}", "{\"locale\":null}", "{}", "tr", "",
            "{\"locale\":\"tr\",\"theme\":\"dark\"}"})
    void gecersizYanitDilBilinmiyorSayilir(String body) {
        TARGET.respondWithJson(body);

        assertThat(client.localeOf(ACCOUNT)).isEmpty();
    }

    @Test
    void zamanAsimindaDilBilinmiyorSayilir() {
        TARGET.respondWithJson("{\"locale\":\"en\"}");
        TARGET.delay(TIMEOUT.multipliedBy(3));

        assertThat(client.localeOf(ACCOUNT)).isEmpty();
    }

    @Test
    void ulasilamayanUserDilBilinmiyorSayilir() {
        String closedUrl;
        try (ErasureTargetStub stopped = ErasureTargetStub.start()) {
            closedUrl = stopped.baseUrl();
        }

        assertThat(client(Map.of("user", URI.create(closedUrl))).localeOf(ACCOUNT)).isEmpty();
    }

    @Test
    void userAdresiYoksaBosDoner() {
        assertThat(client(Map.of("mail", URI.create(TARGET.baseUrl()))).localeOf(ACCOUNT)).isEmpty();
        assertThat(TARGET.received()).isEmpty();
    }

    @Test
    void basariliDegerKisaSureOnbellegeAlinir() {
        TARGET.respondWithJson("{\"locale\":\"en\"}");

        assertThat(client.localeOf(ACCOUNT)).contains("en");
        assertThat(client.localeOf(ACCOUNT)).contains("en");
        assertThat(TARGET.received()).as("ikinci okuma User'a gitmez").hasSize(1);

        NOW.set(NOW.get().plus(HttpUserLocaleClient.CACHE_TTL).plusSeconds(1));
        TARGET.respondWithJson("{\"locale\":\"tr\"}");

        assertThat(client.localeOf(ACCOUNT)).as("süre dolunca yeniden okunur").contains("tr");
        assertThat(TARGET.received()).hasSize(2);
    }

    @Test
    void hataOnbellegeAlinmazProfilSonradanOlusabilir() {
        TARGET.respondWith(404);
        assertThat(client.localeOf(ACCOUNT)).isEmpty();

        TARGET.respondWithJson("{\"locale\":\"en\"}");
        assertThat(client.localeOf(ACCOUNT)).isEqualTo(Optional.of("en"));
    }

    private static HttpUserLocaleClient client(Map<String, URI> serviceUrls) {
        DeletionProperties properties = new DeletionProperties(Set.copyOf(serviceUrls.keySet()), serviceUrls, TIMEOUT,
                Duration.ofMinutes(30), Duration.ofSeconds(60), Duration.ofMinutes(10),
                URI.create("http://localhost:5001/account/delete"), false, Duration.ofSeconds(30), 20,
                Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofHours(6), 10, Duration.ofDays(14));
        return new HttpUserLocaleClient(properties, TestServiceTokens.issuer(Clock.systemUTC()), MUTABLE_CLOCK);
    }
}
