package com.ltz.performance.infrastructure.config;

import com.ltz.performance.infrastructure.client.PerformanceClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PerformanceClientProperties.class)
public class PerformanceClientConfiguration { }
