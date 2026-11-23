package com.ltz.recommendation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** HTTP kota testinin sistem saatinde dakika sınırını geçmesinden etkilenmesini önler. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedRateLimiterConfiguration {
    @Bean
    @Primary
    RecommendationRateLimiter fixedRecommendationRateLimiter() {
        return new RecommendationRateLimiter(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC));
    }
}
