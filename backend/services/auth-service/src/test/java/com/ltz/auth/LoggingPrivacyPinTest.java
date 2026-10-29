package com.ltz.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Gizlilik: genel DEBUG/TRACE (LOG_LEVEL ya da debug bayrağı) açılsa bile parola özeti, e-posta, OAuth code/state/client_secret
 * gibi değerleri yazabilen logger'lar INFO'da kalmalıdır. Pinler {@code application.properties} içinde AÇIKÇA yazılıdır;
 * bu test hangi logger'ın açıkça INFO'ya sabitlendiğini (miras alan değil) doğrular. Kök seviyeyi TRACE'e çeken bir test
 * paylaşılan log durumunu bozacağı için bunun yerine seviyenin açık ayarı denetlenir.
 * <p>
 * Elle doğrulama (en kötü durum): {@code LOG_LEVEL=TRACE mvn -pl services/auth-service test -Dtest='GoogleLoginTest,SteamLoginTest'}
 * 22/22 geçmelidir. Bir satır kaldırılırsa bu test düşer; kaldırmak yerine nedeni önce güvenlik sahibiyle konuşulur.
 */
class LoggingPrivacyPinTest extends IntegrationTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {
            "org.springframework.web.client",
            "org.hibernate.orm.core",
            "org.hibernate.orm.jdbc",
            "org.hibernate.orm.resource",
            "org.postgresql",
            "org.springframework.jdbc",
            "org.springframework.web.method",
            "org.springframework.web.servlet.DispatcherServlet",
            "org.springframework.web.servlet.mvc.method.annotation"
    })
    void gizliDegerYazabilenLoggerGenelSeviyedenAyrilmisVeInfoDadir(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);

        assertThat(logger.getLevel()).as("%s açıkça INFO olmalı (miras alan değil)", loggerName).isEqualTo(Level.INFO);
    }
}
