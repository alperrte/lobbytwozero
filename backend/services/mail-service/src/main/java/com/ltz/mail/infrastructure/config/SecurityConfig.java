package com.ltz.mail.infrastructure.config;

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
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.ltz.mail.api.controller.InternalApiPaths;
import com.ltz.mail.infrastructure.security.RequestBodySizeLimitFilter;
import com.ltz.mail.infrastructure.security.TokenClaims;

import jakarta.servlet.DispatcherType;

/**
 * Fail-closed güvenlik zinciri: yalnız sağlık ucu herkese açıktır; Mail Service'in kullanıcıya açık hiçbir ucu yoktur.
 * İç uçlar ({@code /internal/**}) Auth'un imzaladığı geçerli servis token'ı ({@code svc+jwt}) ister ve uç başına kapsam
 * gerekir ({@code mail:send} gönderim, {@code account:erase} silme); geri kalan her şey reddedilir. Kimlik yalnız
 * {@code Authorization: Bearer} başlığından okunur; servis çerez okumaz ve oturum açmaz, bu yüzden CSRF yüzeyi yoktur.
 * 401/403 yanıtları @RestControllerAdvice'a devredilir; ProblemDetail döner.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String HEALTH = "/actuator/health";
    private static final String SCOPE_PREFIX = "SCOPE_";

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /** Servis token'ıyla çağrılan iç uçlar; tarayıcı oturumu/çerezi yoktur, CSRF burada anlamsızdır. */
    static final RequestMatcher INTERNAL_API = PATHS.matcher(InternalApiPaths.BASE + "/**");

    /** Kimlik gerektirmeyen uçlar. */
    static final RequestMatcher HEALTH_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.GET, HEALTH),
            PATHS.matcher(HttpMethod.GET, HEALTH + "/**"));

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Qualifier("serviceTokenDecoder") JwtDecoder serviceTokenDecoder,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        AuthenticationEntryPoint unauthenticated = (request, response, failure) ->
                resolver.resolveException(request, response, null, failure);
        AccessDeniedHandler forbidden = (request, response, denial) ->
                resolver.resolveException(request, response, null, denial);
        AuthenticationManager serviceTokens = jwtManager(serviceTokenDecoder, scopeConverter());

        http
                // Cookie ile kimlik okunmaz (yalnız Bearer servis token'ı); CSRF koruması KAPATILMAZ, yalnız iç API
                // yollarında muaftır. Diğer yollarda varsayılan koruma açık kalır (CodeQL: disabled CSRF protection).
                .csrf(csrf -> csrf.ignoringRequestMatchers(INTERNAL_API))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HEALTH_ENDPOINTS).permitAll()
                        .requestMatchers(PATHS.matcher(HttpMethod.POST, InternalApiPaths.VERIFICATION_CODES))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.MAIL_SEND_SCOPE)
                        .requestMatchers(PATHS.matcher(HttpMethod.POST, InternalApiPaths.PASSWORD_RESETS))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.MAIL_SEND_SCOPE)
                        .requestMatchers(PATHS.matcher(HttpMethod.POST, InternalApiPaths.ACCOUNT_DELETIONS))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.MAIL_SEND_SCOPE)
                        .requestMatchers(PATHS.matcher(HttpMethod.POST, InternalApiPaths.SECURITY_NOTIFICATIONS))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.MAIL_SEND_SCOPE)
                        .requestMatchers(PATHS.matcher(HttpMethod.DELETE, InternalApiPaths.ACCOUNT_ERASURE))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.ACCOUNT_ERASE_SCOPE)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationManagerResolver(request -> serviceTokens)
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    /** Tüm uçlar için küçük bir gövde sınırı: iç uçlar yalnız birkaç alanlı JSON alır. */
    @Bean
    FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilter(
            RequestLimitProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        FilterRegistrationBean<RequestBodySizeLimitFilter> registration = new FilterRegistrationBean<>(
                new RequestBodySizeLimitFilter(properties.maxBodySize().toBytes(), resolver));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }

    private static AuthenticationManager jwtManager(JwtDecoder decoder, JwtAuthenticationConverter converter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return new ProviderManager(provider);
    }

    /** Servis token'ı: {@code scope} değeri {@code SCOPE_} önekiyle yetkiye çevrilir (ör. SCOPE_mail:send). */
    private static JwtAuthenticationConverter scopeConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName(TokenClaims.SCOPE);
        scopes.setAuthorityPrefix(SCOPE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        return converter;
    }
}
