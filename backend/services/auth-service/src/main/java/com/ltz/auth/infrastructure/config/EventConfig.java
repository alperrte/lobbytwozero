package com.ltz.auth.infrastructure.config;

import java.net.URI;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Olay hedeflerini açılışta denetler: geçerli servis adı, tanımlı iç adres ve prod profilinde https. Tanımsız adresli
 * bir hedef olayları sessizce biriktireceği için servis açılmaz. Hata mesajları adresin içeriğini taşımaz.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventProperties.class)
public class EventConfig {

    private static final String PROD_PROFILE = "prod";
    private static final String HTTPS = "https";
    private static final Pattern TARGET = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");

    EventConfig(EventProperties properties, DeletionProperties deletion, Environment environment) {
        if (properties.maxBackoff().compareTo(properties.initialBackoff()) < 0) {
            throw new IllegalStateException("ltz.auth.events.max-backoff must not be shorter than initial-backoff");
        }
        boolean prod = environment.matchesProfiles(PROD_PROFILE);
        for (String target : properties.targets()) {
            requireTarget(target, deletion.serviceUrls().get(target), prod);
        }
    }

    private static void requireTarget(String target, URI url, boolean prod) {
        if (!TARGET.matcher(target).matches()) {
            throw new IllegalStateException("ltz.auth.events.targets must contain plain service names");
        }
        String property = "ltz.auth.deletion.service-urls." + target;
        if (url == null) {
            throw new IllegalStateException(property + " is required for every event target");
        }
        if (prod && !HTTPS.equalsIgnoreCase(url.getScheme())) {
            throw new IllegalStateException(property + " must use https in the 'prod' profile");
        }
    }
}
