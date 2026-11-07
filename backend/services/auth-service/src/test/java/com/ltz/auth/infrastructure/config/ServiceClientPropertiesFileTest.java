package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * GERÇEK {@code application.properties} dosyasını Spring ortamına yükleyip servis istemcisi ayarlarını bağlar ve açılış
 * denetimini çalıştırır. Kural testleri (ServiceClientConfigTest) yalnız kodu sınar; bu test dosyadaki kayıtların (GAME-26
 * katılımcıları dahil) gerçekten doğru bağlandığını ve geçerli olduğunu kanıtlar. Gizli anahtarlar yalnız test içinde
 * sahte değerle verilir; hiçbir gerçek değer okunmaz.
 */
class ServiceClientPropertiesFileTest {

    private static final String STRONG = "t".repeat(ServiceClientConfig.MIN_SECRET_LENGTH);
    private static final String[] CLIENTS = {
        "ADMIN", "SOCIAL", "COMMUNITY", "MESSAGING", "LFP", "LIBRARY", "HARDWARE", "GAME", "REVIEW",
        "RECOMMENDATION", "CONTENT", "PERFORMANCE"
    };

    @Test
    void gercekDosyaGameKimlikDuzeltmeKayitlariyleAcilir() throws IOException {
        Map<String, Object> secrets = new java.util.HashMap<>();
        for (String client : CLIENTS) {
            secrets.put("AUTH_" + client + "_SERVICE_CLIENT_SECRET", STRONG);
        }
        ServiceClientProperties properties = bind(secrets);

        assertThatCode(() -> new ServiceClientConfig(properties)).doesNotThrowAnyException();
        ServiceClientProperties.Client game = properties.clients().get("game-service");
        assertThat(game.enabled()).isTrue();
        assertThat(game.targets()).containsKeys("review-service", "library-service", "community-service",
                "content-service", "performance-service");
        assertThat(properties.clients().get("content-service").targets())
                .containsEntry("game-service", java.util.Set.of("game-identity:decision:read"));
        assertThat(properties.clients().get("performance-service").targets())
                .containsEntry("game-service", java.util.Set.of("game-identity:decision:read"));
    }

    @Test
    void gizliAnahtarBosKenKayitDevreDisiKalirVeAcilisiBozmaz() throws IOException {
        ServiceClientProperties properties = bind(Map.of());

        assertThatCode(() -> new ServiceClientConfig(properties)).doesNotThrowAnyException();
        assertThat(properties.clients().get("content-service").enabled()).isFalse();
        assertThat(properties.clients().get("performance-service").enabled()).isFalse();
    }

    private static ServiceClientProperties bind(Map<String, Object> secrets) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-secrets", secrets));
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        return Binder.get(environment).bind("ltz.auth.service-clients", ServiceClientProperties.class)
                .orElseThrow(() -> new IllegalStateException("ltz.auth.service-clients bağlanamadı"));
    }
}
