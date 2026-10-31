package com.ltz.performance.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Süresi dolan çit, hız sınırı kovası ve idempotency makbuzu temizliği için zamanlayıcıyı açar. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class PerformanceSchedulingConfiguration { }
