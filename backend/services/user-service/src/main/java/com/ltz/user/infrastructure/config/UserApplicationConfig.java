package com.ltz.user.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({
        AuthProperties.class,
        MediaProperties.class,
        RequestLimitProperties.class,
        SocialProperties.class
})
public class UserApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
