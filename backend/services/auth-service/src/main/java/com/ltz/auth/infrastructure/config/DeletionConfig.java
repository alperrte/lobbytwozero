package com.ltz.auth.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** prod profilinde silme onay bağlantısı https olmak zorundadır; geri çekilme sınırları tutarlı olmalıdır. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeletionProperties.class)
public class DeletionConfig {

    private static final String PROD_PROFILE = "prod";
    private static final String HTTPS = "https";

    DeletionConfig(DeletionProperties properties, Environment environment) {
        if (properties.maxBackoff().compareTo(properties.initialBackoff()) < 0) {
            throw new IllegalStateException("ltz.auth.deletion.max-backoff must not be shorter than initial-backoff");
        }
        if (environment.matchesProfiles(PROD_PROFILE) && !HTTPS.equalsIgnoreCase(properties.linkBaseUrl().getScheme())) {
            throw new IllegalStateException("ltz.auth.deletion.link-base-url must use https in the 'prod' profile");
        }
    }
}
