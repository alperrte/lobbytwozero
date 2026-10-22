package com.ltz.social.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AuthProperties.class,
        FriendRequestProperties.class,
        RequestLimitProperties.class
})
public class SocialApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
