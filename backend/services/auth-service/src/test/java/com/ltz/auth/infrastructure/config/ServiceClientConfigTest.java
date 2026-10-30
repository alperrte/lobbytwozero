package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ServiceClientConfigTest {

    private static final String STRONG_SECRET = "a".repeat(ServiceClientConfig.MIN_SECRET_LENGTH);

    @Test
    void gizliAnahtariBosIstemciDevreDisiOlarakKabulEdilir() {
        assertAccepted("admin", new ServiceClientProperties.Client("", Set.of()));
        assertAccepted("admin", new ServiceClientProperties.Client(null, null));
        ServiceClientProperties empty = new ServiceClientProperties(null);
        assertThatCode(() -> new ServiceClientConfig(empty)).doesNotThrowAnyException();
    }

    @Test
    void gecerliIstemciAcilir() {
        assertAccepted("admin",
                new ServiceClientProperties.Client(STRONG_SECRET, Set.of("account:read", "account:status:write")));
    }

    @Test
    void zayifGizliAnahtarServisiAcmazMesajAnahtariIcermez() {
        String weak = "kisa-anahtar";
        ServiceClientProperties properties =
                properties("admin", new ServiceClientProperties.Client(weak, Set.of("account:read")));

        assertThatThrownBy(() -> new ServiceClientConfig(properties))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least")
                .hasMessageNotContaining(weak);
    }

    @Test
    void kapsamsizVeyaBilinmeyenKapsamliIstemciServisiAcmaz() {
        assertRejected("admin", new ServiceClientProperties.Client(STRONG_SECRET, Set.of()), "scopes");
        // Silme kapsamı yalnız Auth'un kendi çıkış çağrılarındadır; istemciye verilemez.
        assertRejected("admin", new ServiceClientProperties.Client(STRONG_SECRET, Set.of("account:erase")), "scopes");
    }

    @Test
    void gecersizIstemciKimligiServisiAcmaz() {
        assertRejected("Admin_X", new ServiceClientProperties.Client(STRONG_SECRET, Set.of("account:read")), "client id");
    }

    @Test
    void yalnizHedefKapsamliIstemciAcilirKapsamiOlmayanHedefAcmaz() {
        assertAccepted("social-service", targets(Map.of("community-service", Set.of("events:ingest"))));
        // Hedefsiz ve kapsamsız istemci anlamsızdır.
        assertRejected("social-service", targets(Map.of()), "needs scopes or targets");
        assertRejected("social-service", targets(Map.of("community-service", Set.of())), "targets.community-service");
    }

    @Test
    void hedefKapsamlariBilinenListedenOlmaliVeHedefBaskaServisOlmali() {
        // Auth iç kapsamı başka servise, başka servis kapsamı Auth'a verilemez.
        assertRejected("social-service", targets(Map.of("community-service", Set.of("account:read"))),
                "targets.community-service");
        assertRejected("social-service", targets(Map.of("auth-service", Set.of("events:ingest"))), "targets key");
        assertRejected("social-service", targets(Map.of("ltz-api", Set.of("events:ingest"))), "targets key");
        assertRejected("social-service",
                new ServiceClientProperties.Client(STRONG_SECRET, Set.of("events:ingest"), Map.of()), "scopes");
    }

    @Test
    void hesabaBagliKapsamTekBasinaAcilirBagsizKapsamlaKarisamaz() {
        assertAccepted("recommendation-service", targets(Map.of("library-service", Set.of("signals:read"))));
        assertRejected("recommendation-service",
                targets(Map.of("library-service", Set.of("signals:read", "events:ingest"))), "must not mix");
        // Okuma ve silme ayrıdır: istemciye hiçbir hedefte silme kapsamı verilemez.
        assertRejected("recommendation-service", targets(Map.of("library-service", Set.of("account:erase"))),
                "targets.library-service");
    }

    /** Yalnız hedef kapsamlı istemci (Auth'a kapsamı yok). */
    private static ServiceClientProperties.Client targets(Map<String, Set<String>> targets) {
        return new ServiceClientProperties.Client(STRONG_SECRET, Set.of(), targets);
    }

    private static void assertAccepted(String id, ServiceClientProperties.Client client) {
        ServiceClientProperties properties = properties(id, client);
        assertThatCode(() -> new ServiceClientConfig(properties)).doesNotThrowAnyException();
    }

    private static void assertRejected(String id, ServiceClientProperties.Client client, String messagePart) {
        ServiceClientProperties properties = properties(id, client);
        assertThatThrownBy(() -> new ServiceClientConfig(properties))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(messagePart);
    }

    private static ServiceClientProperties properties(String id, ServiceClientProperties.Client client) {
        return new ServiceClientProperties(Map.of(id, client));
    }
}
