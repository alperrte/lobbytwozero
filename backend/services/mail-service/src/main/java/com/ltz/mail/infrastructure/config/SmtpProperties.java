package com.ltz.mail.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * SMTP ayarları (MAIL-10). ENV'ler sağlayıcı seçilene kadar boş olabilir: boşken geliştirmede Mailpit
 * ({@code mailpit:1025}, TLS yok) kullanılır. Üretimde host, gönderen adresi ve TLS zorunludur ({@link MailStartupGuard}).
 * {@code toString()} parolayı göstermez.
 */
@Validated
@ConfigurationProperties("ltz.mail.smtp")
public record SmtpProperties(String host, Integer port, String username, String password, SmtpSecurity security,
                             String fromAddress, @NotNull Duration connectTimeout, @NotNull Duration readTimeout,
                             @NotNull Duration writeTimeout) {

    static final String DEV_HOST = "mailpit";
    static final int DEV_PORT = 1025;
    static final String DEV_FROM = "no-reply@ltz.local";

    public boolean hostConfigured() {
        return host != null && !host.isBlank();
    }

    public String effectiveHost() {
        return hostConfigured() ? host.strip() : DEV_HOST;
    }

    public SmtpSecurity effectiveSecurity() {
        if (security != null) {
            return security;
        }
        return hostConfigured() ? SmtpSecurity.STARTTLS : SmtpSecurity.NONE;
    }

    public int effectivePort() {
        if (port != null) {
            return port;
        }
        if (!hostConfigured()) {
            return DEV_PORT;
        }
        return switch (effectiveSecurity()) {
            case SSL -> 465;
            case STARTTLS -> 587;
            case NONE -> DEV_PORT;
        };
    }

    public String effectiveFromAddress() {
        return fromAddress == null || fromAddress.isBlank() ? DEV_FROM : fromAddress.strip();
    }

    public boolean fromConfigured() {
        return fromAddress != null && !fromAddress.isBlank();
    }

    public boolean authenticated() {
        return username != null && !username.isBlank();
    }

    @Override
    public String toString() {
        return "SmtpProperties[host=" + host + ", port=" + port + ", username=***, password=***, security=" + security + "]";
    }
}
