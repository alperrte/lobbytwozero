package com.ltz.messaging.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.ltz.messaging.api.controller.InternalApiPaths;
import com.ltz.messaging.api.controller.MessagingApiPaths;
import com.ltz.messaging.domain.entity.MessageAttachment;
import com.ltz.messaging.infrastructure.security.RequestBodySizeLimitFilter;
import com.ltz.messaging.infrastructure.security.TokenClaims;

import jakarta.servlet.DispatcherType;

/**
 * Fail-closed güvenlik zinciri: yalnız sağlık ucu herkese açıktır, {@code /api/v1/messaging/**} geçerli kullanıcı
 * token'ı ister, {@code DELETE /internal/v1/accounts/{id}} yalnız {@code scope=account:erase} taşıyan Auth servis
 * token'ıyla açılır; geri kalan her şey reddedilir. Token türü yola göre seçilir: kullanıcı token'ı iç uçta, servis
 * token'ı kullanıcı uçlarında geçmez. Sohbet üyeliği ve
 * grup rolü kontrolü servis katmanındadır (MSG-14). Kimlik yalnız {@code Authorization: Bearer} başlığından okunur;
 * servis çerez okumaz ve oturum açmaz, bu yüzden CSRF filtresi gereksizdir (çerez→Bearer ve CSRF/Origin Gateway'de).
 * 401/403 yanıtları @RestControllerAdvice'a devredilir; ProblemDetail döner.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String HEALTH = "/actuator/health";

    private static final String SCOPE_PREFIX = "SCOPE_";

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    static final RequestMatcher INTERNAL_ENDPOINTS = PATHS.matcher(InternalApiPaths.BASE + "/**");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Qualifier("userTokenDecoder") JwtDecoder userTokenDecoder,
                                            @Qualifier("serviceTokenDecoder") JwtDecoder serviceTokenDecoder,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        AuthenticationEntryPoint unauthenticated = (request, response, failure) ->
                resolver.resolveException(request, response, null, failure);
        AccessDeniedHandler forbidden = (request, response, denial) ->
                resolver.resolveException(request, response, null, denial);
        AuthenticationManager userTokens = jwtManager(userTokenDecoder, roleConverter());
        AuthenticationManager serviceTokens = jwtManager(serviceTokenDecoder, scopeConverter());

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
                        .requestMatchers(PATHS.matcher(HttpMethod.DELETE, InternalApiPaths.ACCOUNT_ERASURE))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.ACCOUNT_ERASE_SCOPE)
                        .requestMatchers(PATHS.matcher(MessagingApiPaths.BASE + "/**")).authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationManagerResolver(
                                request -> INTERNAL_ENDPOINTS.matches(request) ? serviceTokens : userTokens)
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
        // Görsel yükleme (MSG-17): gövde dosyanın kendisidir, sınırı 1 MB (MessageAttachment.MAX_SIZE_BYTES).
        FilterRegistrationBean<RequestBodySizeLimitFilter> registration = new FilterRegistrationBean<>(
                new RequestBodySizeLimitFilter(properties.maxBodySize().toBytes(),
                        PATHS.matcher(HttpMethod.POST, MessagingApiPaths.ATTACHMENTS),
                        MessageAttachment.MAX_SIZE_BYTES, resolver));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }

    private static AuthenticationManager jwtManager(JwtDecoder decoder, JwtAuthenticationConverter converter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return new ProviderManager(provider);
    }

    /** Servis token'ı: {@code scope} değerleri {@code SCOPE_} önekiyle yetkiye çevrilir; {@code roles} okunmaz. */
    private static JwtAuthenticationConverter scopeConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName(TokenClaims.SCOPE);
        scopes.setAuthorityPrefix(SCOPE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        return converter;
    }

    /** Kullanıcı token'ı: {@code roles} listesi {@code ROLE_} önekiyle yetkiye çevrilir. */
    private static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenClaims.ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
