package com.ltz.messaging.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.ltz.messaging.api.controller.MessagingApiPaths;
import com.ltz.messaging.infrastructure.security.RequestBodySizeLimitFilter;
import com.ltz.messaging.infrastructure.security.TokenClaims;

import jakarta.servlet.DispatcherType;

/**
 * Fail-closed güvenlik zinciri: yalnız sağlık ucu herkese açıktır, {@code /api/v1/messaging/**} geçerli kullanıcı
 * token'ı ister, geri kalan her şey (iç uçlar dahil; Messaging'in henüz iç ucu yoktur) reddedilir. Sohbet üyeliği ve
 * grup rolü kontrolü servis katmanındadır (MSG-14). Kimlik yalnız {@code Authorization: Bearer} başlığından okunur;
 * servis çerez okumaz ve oturum açmaz, bu yüzden CSRF filtresi gereksizdir (çerez→Bearer ve CSRF/Origin Gateway'de).
 * 401/403 yanıtları @RestControllerAdvice'a devredilir; ProblemDetail döner.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String HEALTH = "/actuator/health";

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        AuthenticationEntryPoint unauthenticated = (request, response, failure) ->
                resolver.resolveException(request, response, null, failure);
        AccessDeniedHandler forbidden = (request, response, denial) ->
                resolver.resolveException(request, response, null, denial);

        http
                // Cookie ile kimlik okunmadığından CSRF saldırı yüzeyi yoktur; stateless Bearer API.
                .csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // ASYNC: SSE akışının devam dispatch'i; ilk istek zaten kimlik doğrulamasından geçmiştir.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(PATHS.matcher(HttpMethod.GET, HEALTH),
                                PATHS.matcher(HttpMethod.GET, HEALTH + "/**")).permitAll()
                        .requestMatchers(PATHS.matcher(MessagingApiPaths.BASE + "/**")).authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter()))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    @Bean
    FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilter(
            RequestLimitProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        FilterRegistrationBean<RequestBodySizeLimitFilter> registration = new FilterRegistrationBean<>(
                new RequestBodySizeLimitFilter(properties.maxBodySize().toBytes(), resolver));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }

    /** {@code roles} listesi {@code ROLE_} önekiyle yetkiye çevrilir. */
    private static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenClaims.ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
