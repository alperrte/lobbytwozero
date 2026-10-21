package com.ltz.auth.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Açılışta adaptör ile profilin uyumunu denetler (fail-fast):
 * <ul>
 *   <li>DEV_SMTP yalnız dev profilinde ve prod profili yokken kullanılabilir.</li>
 *   <li>prod profili yalnız production'a hazır bir adaptörle başlar; gerçek Mail Service adaptörü
 *       eklenene kadar production açılışı bilerek reddedilir.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailDeliveryProperties.class)
public class MailDeliveryConfig {

    static final String DEV_PROFILE = "dev";
    static final String PROD_PROFILE = "prod";

    MailDeliveryConfig(MailDeliveryProperties properties, Environment environment) {
        MailAdapter adapter = properties.adapter();
        boolean prod = environment.matchesProfiles(PROD_PROFILE);
        if (adapter == MailAdapter.DEV_SMTP && (prod || !environment.matchesProfiles(DEV_PROFILE))) {
            throw new IllegalStateException(
                    "ltz.auth.mail.adapter=dev-smtp is only allowed with the 'dev' profile and never with 'prod'");
        }
        if (prod && !adapter.isProductionReady()) {
            throw new IllegalStateException("Production mail delivery is not configured (ltz.auth.mail.adapter="
                    + adapter + "); Mail Service integration is PENDING");
        }
    }
}
