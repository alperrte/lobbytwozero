package com.ltz.auth.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        VerificationProperties.class,
        PasswordHashingProperties.class,
        RequestLimitProperties.class,
        JwtProperties.class,
        SessionProperties.class,
        AttemptProperties.class,
        TwoFactorProperties.class
})
public class AuthApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
