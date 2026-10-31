package com.ltz.review.infrastructure.config;

import com.ltz.review.application.service.AbuseQuotaService;
import com.ltz.review.domain.enums.AbuseAction;
import com.ltz.review.infrastructure.security.AbuseQuotaInterceptor;
import java.time.Clock;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AbuseControlProperties.class)
public class AbuseControlConfiguration implements WebMvcConfigurer {
    private final AbuseQuotaService quotas;

    public AbuseControlConfiguration(AbuseQuotaService quotas) { this.quotas = quotas; }

    @Bean
    static Clock abuseControlClock() { return Clock.systemUTC(); }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AbuseQuotaInterceptor(quotas, AbuseAction.REVIEW_CREATE, Set.of("POST")))
                .addPathPatterns("/api/v1/review/games/*/reviews");
        registry.addInterceptor(new AbuseQuotaInterceptor(quotas, AbuseAction.REVIEW_LIKE, Set.of("PUT", "DELETE")))
                .addPathPatterns("/api/v1/review/reviews/*/like");
    }
}
