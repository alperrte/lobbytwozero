package com.ltz.auth.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** prod profilinde e-postadaki sıfırlama bağlantısı https olmak zorundadır; aksi halde servis başlamaz. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetConfig {

    private static final String PROD_PROFILE = "prod";
    private static final String HTTPS = "https";

    PasswordResetConfig(PasswordResetProperties properties, Environment environment) {
        if (environment.matchesProfiles(PROD_PROFILE) && !HTTPS.equalsIgnoreCase(properties.linkBaseUrl().getScheme())) {
            throw new IllegalStateException("ltz.auth.password-reset.link-base-url must use https in the 'prod' profile");
        }
    }
}
