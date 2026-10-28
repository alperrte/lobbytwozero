package com.ltz.auth.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import com.ltz.auth.application.port.GoogleOidcPort;
import com.ltz.auth.application.port.SteamAssertionVerifier;
import com.ltz.auth.infrastructure.client.GoogleOidcClient;
import com.ltz.auth.infrastructure.client.SteamOpenIdClient;

import jakarta.annotation.PreDestroy;

/**
 * Harici giriş istemcileri. Sağlayıcı çağrıları yönlendirme izlemez ve zaman aşımına tabidir.
 * prod profilinde tüm adresler https olmalıdır; yarım Google kimlik bilgisi (yalnız id veya yalnız secret) her
 * profilde açılışı durdurur.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthConfig {

    static final String PROD_PROFILE = "prod";
    private static final String HTTPS = "https";

    private HttpClient httpClient;

    OAuthConfig(OAuthProperties properties, Environment environment) {
        OAuthProperties.Google google = properties.google();
        boolean idSet = google.clientId() != null && !google.clientId().isBlank();
        boolean secretSet = google.clientSecret() != null && !google.clientSecret().isBlank();
        if (idSet != secretSet) {
            throw new IllegalStateException("ltz.auth.oauth.google client id and client secret must be set together");
        }
        if (environment.matchesProfiles(PROD_PROFILE)) {
            requireHttps(properties.publicBaseUrl().getScheme(), "public-base-url");
            requireHttps(properties.frontendRedirectUrl().getScheme(), "frontend-redirect-url");
            requireHttps(properties.steam().openIdEndpoint().getScheme(), "steam.open-id-endpoint");
            requireHttps(google.authorizationUri().getScheme(), "google.authorization-uri");
            requireHttps(google.tokenUri().getScheme(), "google.token-uri");
            requireHttps(google.jwksUri().getScheme(), "google.jwks-uri");
        }
    }

    @Bean
    GoogleOidcPort googleOidcPort(OAuthProperties properties, Clock clock) {
        return new GoogleOidcClient(properties.google(), requestFactory(properties), clock);
    }

    @Bean
    SteamAssertionVerifier steamAssertionVerifier(OAuthProperties properties) {
        return new SteamOpenIdClient(properties.steam(), requestFactory(properties));
    }

    /** Google ve Steam istemcileri aynı yönlendirmesiz, zaman aşımlı HTTP istemcisini paylaşır; sahibi bu yapılandırmadır. */
    private ClientHttpRequestFactory requestFactory(OAuthProperties properties) {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(properties.httpTimeout())
                    .build();
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.httpTimeout());
        return factory;
    }

    /** Bağlam kapanırken paylaşılan HTTP istemcisini kapatır (bağlantı havuzu ve iş parçacıkları kalmaz). */
    @PreDestroy
    void close() {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    private static void requireHttps(String scheme, String property) {
        if (!HTTPS.equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("ltz.auth.oauth." + property + " must use https in the 'prod' profile");
        }
    }
}
