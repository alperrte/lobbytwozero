package com.ltz.mail.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Üretim açılış korumaları (C076/C077): prod profilinde SMTP sunucusu, gönderen adresi, TLS ve izinli bağlantı
 * hostları zorunludur; geliştirme kolaylıkları üretime sızamaz. Saf birim testi, Spring bağlamı gerekmez.
 */
class MailStartupGuardTest {

    private static SmtpProperties smtp(String host, SmtpSecurity security, String from) {
        return new SmtpProperties(host, null, null, null, security, from, Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofSeconds(5));
    }

    private static LinkProperties links(String... hosts) {
        return new LinkProperties(List.of(hosts));
    }

    private static PlatformProperties noCallers() {
        return new PlatformProperties(Map.of());
    }

    private static PlatformProperties oneCaller() {
        return new PlatformProperties(Map.of("review-service", new PlatformProperties.Caller(null, null)));
    }

    private static AuthClientProperties authClient(String secret) {
        return new AuthClientProperties("http://auth-service:8080", "mail-service", secret, "/token", "/contact");
    }

    private static MockEnvironment profile(String name) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(name);
        return environment;
    }

    @Test
    void prodProfilindeTamYapilandirmaKabulEdilir() {
        assertThatCode(() -> new MailStartupGuard(smtp("smtp.example", SmtpSecurity.STARTTLS, "no-reply@ltz.example"),
                links("ltz.example"), noCallers(), authClient(null), profile("prod"))).doesNotThrowAnyException();
    }

    @Test
    void prodDisindaEksikYapilandirmaAcilisiDurdurmaz() {
        assertThatCode(() -> new MailStartupGuard(smtp(null, null, null), links(), noCallers(), authClient(null),
                profile("dev"))).doesNotThrowAnyException();
    }

    @Test
    void prodProfilindeSmtpSunucusuYoksaAcilisDurur() {
        assertThatThrownBy(() -> new MailStartupGuard(smtp(null, SmtpSecurity.STARTTLS, "no-reply@ltz.example"),
                links("ltz.example"), noCallers(), authClient(null), profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_SMTP_HOST");
    }

    @Test
    void prodProfilindeGonderenAdresiYoksaAcilisDurur() {
        assertThatThrownBy(() -> new MailStartupGuard(smtp("smtp.example", SmtpSecurity.STARTTLS, " "),
                links("ltz.example"), noCallers(), authClient(null), profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_FROM_ADDRESS");
    }

    @Test
    void prodProfilindeTlsKapaliysaAcilisDurur() {
        assertThatThrownBy(() -> new MailStartupGuard(
                smtp("smtp.example", SmtpSecurity.NONE, "no-reply@ltz.example"), links("ltz.example"), noCallers(),
                authClient(null), profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TLS");
    }

    @Test
    void prodProfilindeIzinliBaglantiHostuYoksaAcilisDurur() {
        assertThatThrownBy(() -> new MailStartupGuard(
                smtp("smtp.example", SmtpSecurity.SSL, "no-reply@ltz.example"), links(), noCallers(), authClient(null),
                profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_ALLOWED_LINK_HOSTS");
    }

    @Test
    void platformCagiranlariVarkenAuthIstemciAnahtariZorunludur() {
        assertThatThrownBy(() -> new MailStartupGuard(
                smtp("smtp.example", SmtpSecurity.STARTTLS, "no-reply@ltz.example"), links("ltz.example"), oneCaller(),
                authClient(" "), profile("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("AUTH_MAIL_SERVICE_CLIENT_SECRET");
        assertThatCode(() -> new MailStartupGuard(
                smtp("smtp.example", SmtpSecurity.STARTTLS, "no-reply@ltz.example"), links("ltz.example"), oneCaller(),
                authClient("secret-value"), profile("prod"))).doesNotThrowAnyException();
    }
}
