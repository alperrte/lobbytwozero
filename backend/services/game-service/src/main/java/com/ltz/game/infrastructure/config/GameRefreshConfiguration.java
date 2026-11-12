package com.ltz.game.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Veri tazelik ayarlarını ({@code ltz.game.refresh.*}) bağlar; yeni ENV değişkeni gerektirmez. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GameRefreshProperties.class)
public class GameRefreshConfiguration {
}
