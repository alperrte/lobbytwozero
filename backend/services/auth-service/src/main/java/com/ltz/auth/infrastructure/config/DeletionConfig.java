package com.ltz.auth.infrastructure.config;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * prod profilinde silme onay bağlantısı https olmak zorundadır; geri çekilme sınırları tutarlı olmalıdır. Her silme
 * hedefinin iç adresi tanımlı ve yalnız şema + host (+ port) olmalıdır: adresi eksik hedef, görevlerin sessizce
 * takılmasına yol açacağı için servis açılmaz.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeletionProperties.class)
public class DeletionConfig {

    private static final String PROD_PROFILE = "prod";
    private static final String HTTPS = "https";
    private static final Set<String> SERVICE_URL_SCHEMES = Set.of("http", HTTPS);

    DeletionConfig(DeletionProperties properties, Environment environment) {
        if (properties.maxBackoff().compareTo(properties.initialBackoff()) < 0) {
            throw new IllegalStateException("ltz.auth.deletion.max-backoff must not be shorter than initial-backoff");
        }
        boolean prod = environment.matchesProfiles(PROD_PROFILE);
        if (prod && !HTTPS.equalsIgnoreCase(properties.linkBaseUrl().getScheme())) {
            throw new IllegalStateException("ltz.auth.deletion.link-base-url must use https in the 'prod' profile");
        }
        for (String target : properties.targets()) {
            requireServiceUrl(target, properties.serviceUrls().get(target), prod);
        }
    }

    /**
     * Hedef servis çağrısı servis token'ını taşır; prod profilinde iç ağda da şifresiz (http) gönderilmez, böylece ağı
     * dinleyen biri 60 sn'lik token'ı yeniden oynatamaz (B18).
     */
    private static void requireServiceUrl(String target, URI url, boolean prod) {
        String property = "ltz.auth.deletion.service-urls." + target;
        if (url == null) {
            throw new IllegalStateException(property + " is required for every deletion target");
        }
        if (!isOrigin(url)) {
            throw new IllegalStateException(property + " must be an http(s) origin without path, credentials or query");
        }
        if (prod && !HTTPS.equalsIgnoreCase(url.getScheme())) {
            throw new IllegalStateException(property + " must use https in the 'prod' profile");
        }
    }

    private static boolean isOrigin(URI url) {
        if (url.getScheme() == null || !SERVICE_URL_SCHEMES.contains(url.getScheme().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (url.getHost() == null || url.getRawUserInfo() != null) {
            return false;
        }
        if (url.getRawQuery() != null || url.getRawFragment() != null) {
            return false;
        }
        String path = url.getRawPath();
        return path == null || path.isEmpty() || "/".equals(path);
    }
}
