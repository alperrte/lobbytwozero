package com.ltz.mail.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Açılışta üretim güvenlik koşullarını denetler (fail-fast): {@code prod} profilinde SMTP sunucusu, gönderen adresi ve
 * izinli bağlantı hostları tanımlı olmalı ve aktarım TLS ile korunmalıdır. Geliştirme kolaylıkları (Mailpit, TLS yok,
 * yerel bağlantılar) üretime sızamaz. Şifreleme anahtarının geçerliliği {@code AesGcmPayloadCipher} açılışında denetlenir.
 */
@Configuration(proxyBeanMethods = false)
public class MailStartupGuard {

    static final String PROD_PROFILE = "prod";

    MailStartupGuard(SmtpProperties smtp, LinkProperties link, Environment environment) {
        if (!environment.matchesProfiles(PROD_PROFILE)) {
            return;
        }
        if (!smtp.hostConfigured()) {
            throw new IllegalStateException("Production requires MAIL_SMTP_HOST");
        }
        if (!smtp.fromConfigured()) {
            throw new IllegalStateException("Production requires MAIL_FROM_ADDRESS");
        }
        if (smtp.effectiveSecurity() == SmtpSecurity.NONE) {
            throw new IllegalStateException("Production requires TLS (MAIL_SMTP_SECURITY must be STARTTLS or SSL)");
        }
        if (link.allowedHosts().isEmpty()) {
            throw new IllegalStateException("Production requires MAIL_ALLOWED_LINK_HOSTS");
        }
    }
}
