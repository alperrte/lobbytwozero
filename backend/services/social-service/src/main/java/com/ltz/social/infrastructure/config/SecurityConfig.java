package com.ltz.social.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.ltz.social.api.controller.SocialApiPaths;
import com.ltz.social.infrastructure.security.RequestBodySizeLimitFilter;
import com.ltz.social.infrastructure.security.TokenClaims;

import jakarta.servlet.DispatcherType;

/**
 * Fail-closed güvenlik zinciri: yalnız sağlık ucu herkese açıktır, Social uçları geçerli access token ister,
 * geri kalan her şey reddedilir. Kimlik yalnız {@code Authorization: Bearer} başlığından okunur; servis çerez
 * okumaz ve oturum açmaz. Tarayıcı çerezi → Bearer çevirisi ve CSRF/Origin denetimi Gateway'de yapılır
 * (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §6.1), bu yüzden bu zincirde CSRF filtresi gereksizdir.
 * 401/403 yanıtları @RestControllerAdvice'a devredilir; güvenlik hataları da ProblemDetail döner.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String HEALTH = "/actuator/health";
    private static final String ROLE_PREFIX = "ROLE_";

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /** Kimlik gerektirmeyen uçlar. */
    static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.GET, HEALTH),
            PATHS.matcher(HttpMethod.GET, HEALTH + "/**"));

    /** Geçerli access token isteyen uçlar (tüm kayıtlı oyuncular; sahiplik kontrolü servis katmanındadır). */
    static final RequestMatcher AUTHENTICATED_ENDPOINTS = PATHS.matcher(SocialApiPaths.BASE + "/**");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
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
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(AUTHENTICATED_ENDPOINTS).authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(roleConverter()))
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

    private static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenClaims.ROLES);
        roles.setAuthorityPrefix(ROLE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
