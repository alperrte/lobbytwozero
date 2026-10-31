package com.ltz.mail.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({
        AuthProperties.class,
        CryptoProperties.class,
        LinkProperties.class,
        RequestLimitProperties.class,
        SmtpProperties.class,
        WorkerProperties.class
})
public class MailApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
