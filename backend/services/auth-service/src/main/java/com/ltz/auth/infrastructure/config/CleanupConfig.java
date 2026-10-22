package com.ltz.auth.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Temizlik sürelerinin güvenlik açısından tutarlılığını açılışta denetler (fail-fast). Çok kısa tutulan bir kayıt,
 * replay veya brute-force korumasını sessizce zayıflatabilir.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CleanupProperties.class)
public class CleanupConfig {

    private static final int NONCE_WINDOW_FACTOR = 2;

    CleanupConfig(CleanupProperties cleanup, OAuthProperties oauth, AttemptProperties attempts) {
        if (cleanup.nonceRetention().compareTo(oauth.steam().assertionMaxAge().multipliedBy(NONCE_WINDOW_FACTOR)) < 0) {
            throw new IllegalStateException(
                    "ltz.auth.cleanup.nonce-retention must be at least twice ltz.auth.oauth.steam.assertion-max-age");
        }
        if (cleanup.throttleRetention().compareTo(attempts.window().plus(attempts.lockDuration())) < 0) {
            throw new IllegalStateException(
                    "ltz.auth.cleanup.throttle-retention must cover ltz.auth.attempts.window plus lock-duration");
        }
    }
}
