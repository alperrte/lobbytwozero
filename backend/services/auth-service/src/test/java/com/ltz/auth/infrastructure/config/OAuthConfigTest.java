package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Harici giriş yapılandırma korumaları (C005/C012): prod'da tüm sağlayıcı ve yönlendirme adresleri https olmalı,
 * yarım Google kimlik bilgisi açılışı durdurmalıdır. Saf birim testi, Spring bağlamı gerekmez.
 */
class OAuthConfigTest {

    private static final String HTTPS = "https://ltz.example";
    private static final String HTTP = "http://ltz.example";

    private static OAuthProperties properties(String publicBase, String frontend, String steam, String google,
                                              String clientId, String clientSecret) {
        return new OAuthProperties(URI.create(publicBase), URI.create(frontend), Duration.ofMinutes(10),
                Duration.ofSeconds(3),
                new OAuthProperties.Google(clientId, clientSecret, URI.create(google + "/auth"),
                        URI.create(google + "/token"), URI.create(google + "/jwks"), "https://accounts.google.com",
                        Duration.ofMinutes(15)),
                new OAuthProperties.Steam(URI.create(steam + "/openid/login"), Duration.ofMinutes(5),
                        Duration.ofMinutes(15), Duration.ofMinutes(15)));
    }

    private static MockEnvironment profile(String name) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(name);
        return environment;
    }

    @Test
    void prodProfilindeTumAdreslerHttpsIseKabulEdilir() {
        OAuthProperties all = properties(HTTPS, HTTPS, "https://steamcommunity.com", "https://google.example", "id",
                "secret");
        assertThatCode(() -> new OAuthConfig(all, profile("prod"))).doesNotThrowAnyException();
    }

    @Test
    void prodProfilindeHttpAdresleriAcilisiDurdurur() {
        String https = "https://steamcommunity.com";
        assertThatThrownBy(() -> new OAuthConfig(
                properties(HTTP, HTTPS, https, "https://google.example", "id", "secret"), profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("public-base-url");
        assertThatThrownBy(() -> new OAuthConfig(
                properties(HTTPS, HTTP, https, "https://google.example", "id", "secret"), profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("frontend-redirect-url");
        assertThatThrownBy(() -> new OAuthConfig(
                properties(HTTPS, HTTPS, "http://steamcommunity.com", "https://google.example", "id", "secret"),
                profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("steam.open-id-endpoint");
        assertThatThrownBy(() -> new OAuthConfig(
                properties(HTTPS, HTTPS, https, "http://google.example", "id", "secret"), profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("google.");
    }

    @Test
    void prodDisindaYerelHttpAdreslerineIzinVerilir() {
        OAuthProperties local = properties("http://localhost:8080", "http://localhost:5001", "http://localhost:9",
                "http://localhost:9", "id", "secret");
        assertThatCode(() -> new OAuthConfig(local, profile("dev"))).doesNotThrowAnyException();
    }

    @Test
    void yarimGoogleKimlikBilgisiHerProfildeAcilisiDurdurur() {
        assertThatThrownBy(() -> new OAuthConfig(
                properties(HTTPS, HTTPS, "https://steamcommunity.com", "https://google.example", "id", ""),
                profile("dev")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("set together");
        assertThatThrownBy(() -> new OAuthConfig(
                properties(HTTPS, HTTPS, "https://steamcommunity.com", "https://google.example", "", "secret"),
                profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("set together");
    }

    @Test
    void googleKimlikBilgisiHicYoksaGirisKapaliOlarakAcilir() {
        OAuthProperties none = properties(HTTPS, HTTPS, "https://steamcommunity.com", "https://google.example", "",
                "");
        assertThatCode(() -> new OAuthConfig(none, profile("prod"))).doesNotThrowAnyException();
    }
}
