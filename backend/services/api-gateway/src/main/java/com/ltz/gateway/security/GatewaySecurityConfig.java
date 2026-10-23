package com.ltz.gateway.security;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS ve Gateway güvenlik filtreleri. Sıra: istemci başlık temizliği (en önce) → CORS → Auth hız sınırı → çerez/Bearer
 * çevirisi. CORS, hız sınırı ve CSRF hatalarının tarayıcıda okunabilmesi için hız sınırından önce çalışır.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfig {

    private static final String PROD_PROFILE = "prod";

    GatewaySecurityConfig(GatewaySecurityProperties properties, Environment environment) {
        if (environment.matchesProfiles(PROD_PROFILE) && !properties.cors().allHttps()) {
            throw new IllegalStateException("ltz.gateway.cors.allowed-origins must use https in the 'prod' profile");
        }
    }

    @Bean
    Clock gatewayClock() {
        return Clock.systemUTC();
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilter(GatewaySecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN", "X-Request-Id", "Accept"));
        cors.setExposedHeaders(List.of("Retry-After", "X-Request-Id"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(1800L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(10);
        return registration;
    }
}
