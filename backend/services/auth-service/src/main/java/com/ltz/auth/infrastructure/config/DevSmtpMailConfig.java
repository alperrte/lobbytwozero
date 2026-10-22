package com.ltz.auth.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.ltz.auth.infrastructure.client.DevSmtpVerificationMailAdapter;

/**
 * Yalnız development: profil ve adaptör seçimi birlikte sağlanmadıkça bu sınıf hiç yüklenmez.
 * dev ve prod birlikte seçilse bile "!prod" koşulu nedeniyle devre dışı kalır.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
@ConditionalOnProperty(name = "ltz.auth.mail.adapter", havingValue = "dev-smtp")
@EnableConfigurationProperties(DevSmtpProperties.class)
public class DevSmtpMailConfig {

    @Bean
    DevSmtpVerificationMailAdapter devSmtpVerificationMailAdapter(DevSmtpProperties properties) {
        return new DevSmtpVerificationMailAdapter(properties);
    }
}
