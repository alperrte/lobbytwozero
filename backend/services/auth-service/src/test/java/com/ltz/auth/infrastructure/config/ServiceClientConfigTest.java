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
        assertThatCode(() -> new ServiceClientConfig(properties("admin", new ServiceClientProperties.Client("", Set.of()))))
                .doesNotThrowAnyException();
        assertThatCode(() -> new ServiceClientConfig(properties("admin", new ServiceClientProperties.Client(null, null))))
                .doesNotThrowAnyException();
        assertThatCode(() -> new ServiceClientConfig(new ServiceClientProperties(null))).doesNotThrowAnyException();
    }

    @Test
    void gecerliIstemciAcilir() {
        assertThatCode(() -> new ServiceClientConfig(properties("admin",
                new ServiceClientProperties.Client(STRONG_SECRET, Set.of("account:read", "account:status:write")))))
                .doesNotThrowAnyException();
    }

    @Test
    void zayifGizliAnahtarServisiAcmazMesajAnahtariIcermez() {
        String weak = "kisa-anahtar";

        assertThatThrownBy(() -> new ServiceClientConfig(properties("admin",
                new ServiceClientProperties.Client(weak, Set.of("account:read")))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least")
                .hasMessageNotContaining(weak);
    }

    @Test
    void kapsamsizVeyaBilinmeyenKapsamliIstemciServisiAcmaz() {
        assertThatThrownBy(() -> new ServiceClientConfig(properties("admin",
                new ServiceClientProperties.Client(STRONG_SECRET, Set.of()))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("scopes");
        // Silme kapsamı yalnız Auth'un kendi çıkış çağrılarındadır; istemciye verilemez.
        assertThatThrownBy(() -> new ServiceClientConfig(properties("admin",
                new ServiceClientProperties.Client(STRONG_SECRET, Set.of("account:erase")))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("scopes");
    }

    @Test
    void gecersizIstemciKimligiServisiAcmaz() {
        assertThatThrownBy(() -> new ServiceClientConfig(properties("Admin_X",
                new ServiceClientProperties.Client(STRONG_SECRET, Set.of("account:read")))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("client id");
    }

    @Test
    void yalnizHedefKapsamliIstemciAcilirKapsamiOlmayanHedefAcmaz() {
        assertThatCode(() -> new ServiceClientConfig(properties("social-service", new ServiceClientProperties.Client(
                STRONG_SECRET, Set.of(), Map.of("community-service", Set.of("events:ingest"))))))
                .doesNotThrowAnyException();
        // Hedefsiz ve kapsamsız istemci anlamsızdır.
        assertThatThrownBy(() -> new ServiceClientConfig(properties("social-service", new ServiceClientProperties.Client(
                STRONG_SECRET, Set.of(), Map.of()))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("needs scopes or targets");
        assertThatThrownBy(() -> new ServiceClientConfig(properties("social-service", new ServiceClientProperties.Client(
                STRONG_SECRET, Set.of(), Map.of("community-service", Set.of())))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("targets.community-service");
    }

    @Test
    void hedefKapsamlariBilinenListedenOlmaliVeHedefBaskaServisOlmali() {
        // Auth iç kapsamı başka servise, başka servis kapsamı Auth'a verilemez.
        assertThatThrownBy(() -> new ServiceClientConfig(properties("social-service", new ServiceClientProperties.Client(
                STRONG_SECRET, Set.of(), Map.of("community-service", Set.of("account:read"))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("targets.community-service");
        assertThatThrownBy(() -> new ServiceClientConfig(properties("social-service", new ServiceClientProperties.Client(
                STRONG_SECRET, Set.of(), Map.of("auth-service", Set.of("events:ingest"))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("targets key");
        assertThatThrownBy(() -> new ServiceClientConfig(properties("social-service", new ServiceClientProperties.Client(
                STRONG_SECRET, Set.of(), Map.of("ltz-api", Set.of("events:ingest"))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("targets key");
        assertThatThrownBy(() -> new ServiceClientConfig(properties("social-service", new ServiceClientProperties.Client(
                STRONG_SECRET, Set.of("events:ingest"), Map.of()))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("scopes");
    }

    private static ServiceClientProperties properties(String id, ServiceClientProperties.Client client) {
        return new ServiceClientProperties(Map.of(id, client));
    }
}
