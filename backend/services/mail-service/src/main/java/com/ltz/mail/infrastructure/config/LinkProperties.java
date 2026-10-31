package com.ltz.mail.infrastructure.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * E-postadaki bağlantılar için izinli hostlar. Boş liste yalnız yerel adreslere (localhost, 127.0.0.1) izin verir;
 * üretimde boş olamaz ({@link MailStartupGuard}).
 */
@Validated
@ConfigurationProperties("ltz.mail.link")
public record LinkProperties(List<String> allowedHosts) {

    public LinkProperties {
        allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
    }
}
