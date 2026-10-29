package com.ltz.auth.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.api.controller.SessionCookies;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.infrastructure.security.AccessTokenCookieFilter;
import com.ltz.auth.infrastructure.security.AccessTokenResolver;
import com.ltz.auth.infrastructure.security.ProblemDetailSecurityHandler;
import com.ltz.auth.infrastructure.security.RequestBodySizeLimitFilter;
import com.ltz.auth.infrastructure.security.RequestIdFilter;
import com.ltz.auth.infrastructure.security.TokenClaims;

import jakarta.servlet.DispatcherType;

/**
 * Fail-closed güvenlik zinciri: public uçlar açıkça listelenir, korumalı uçlar geçerli access token ister,
 * geri kalan her şey reddedilir.
 * <ul>
 *   <li>Kimlik: RS256 access token, Authorization başlığından veya HttpOnly cookie'den (D1/D2).</li>
 *   <li>CSRF: cookie okuyan veya oturum açan tüm durum değiştiren isteklerde zorunlu (XSRF-TOKEN cookie +
 *       X-XSRF-TOKEN başlığı). Authorization başlığı taşıyan istekler tarayıcının otomatik gönderebileceği
 *       bir kimlik içermediğinden muaftır. Cookie kullanmayan kayıt/doğrulama uçları (Faz 1) muaftır.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String HEALTH = "/actuator/health";
    private static final String HEALTH_GROUPS = HEALTH + "/**";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";
    private static final String CSRF_COOKIE_PATH = "/";
    private static final String CSRF_COOKIE_SAME_SITE = "Strict";

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /** Kimlik gerektirmeyen uçlar. Bu uçlarda access token okunmaz. */
    static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.REGISTER),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.EMAIL_VERIFICATION_CONFIRM),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.EMAIL_VERIFICATION_RESEND),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.LOGIN),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.LOGIN_TWO_FACTOR),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.SESSION_REFRESH),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.SESSION_LOGOUT),
            PATHS.matcher(HttpMethod.GET, AuthApiPaths.CSRF),
            PATHS.matcher(HttpMethod.GET, AuthApiPaths.JWKS),
            PATHS.matcher(HttpMethod.GET, AuthApiPaths.GOOGLE_START),
            PATHS.matcher(HttpMethod.GET, AuthApiPaths.GOOGLE_CALLBACK),
            PATHS.matcher(HttpMethod.GET, AuthApiPaths.STEAM_START),
            PATHS.matcher(HttpMethod.GET, AuthApiPaths.STEAM_CALLBACK),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.STEAM_COMPLETE),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.PASSWORD_FORGOT),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.PASSWORD_RESET),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.ACCOUNT_DELETION_CANCEL),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.SERVICE_TOKENS),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.DEVICES_TOKEN),
            PATHS.matcher(HttpMethod.GET, HEALTH),
            PATHS.matcher(HttpMethod.GET, HEALTH_GROUPS));

    /** Geçerli access token isteyen uçlar. */
    static final RequestMatcher AUTHENTICATED_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.SESSION_LOGOUT_ALL),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.PASSWORD_CHANGE),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.EMAIL_CHANGE),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.EMAIL_CHANGE_CONFIRM),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.ACCOUNT_DELETION_REQUEST),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.ACCOUNT_DELETION_CONFIRM),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.TOTP_SETUP),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.TOTP_ACTIVATE),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.TOTP_DISABLE),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.DEVICES),
            PATHS.matcher(HttpMethod.GET, AuthApiPaths.DEVICES),
            PATHS.matcher(HttpMethod.DELETE, AuthApiPaths.DEVICE));

    /** Yönetici uçları: geçerli token + ADMIN rolü; aksi 401/403. Rol token'dan değil her istekte doğrulanan oturumdan gelir. */
    static final RequestMatcher ADMIN_ENDPOINTS = PATHS.matcher(AuthApiPaths.ADMIN + "/**");

    /** Servis token'ı isteyen iç uçlar (Gateway'e açılmaz): cookie okunmaz, kapsam yetkisi SCOPE_ ile denetlenir. */
    static final RequestMatcher INTERNAL_ENDPOINTS = PATHS.matcher(AuthApiPaths.INTERNAL + "/**");

    /** Cookie okumayan ve oturum açmayan uçlar (yetki kaynağı gövdedeki tek kullanımlık değerdir). */
    private static final RequestMatcher COOKIELESS_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.REGISTER),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.EMAIL_VERIFICATION_CONFIRM),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.EMAIL_VERIFICATION_RESEND),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.STEAM_COMPLETE),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.PASSWORD_FORGOT),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.PASSWORD_RESET),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.ACCOUNT_DELETION_CANCEL),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.SERVICE_TOKENS),
            PATHS.matcher(HttpMethod.POST, AuthApiPaths.DEVICES_TOKEN));

    private static final RequestMatcher BEARER_HEADER = request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProblemDetailSecurityHandler problemDetailSecurityHandler,
                                            JwtDecoder jwtDecoder,
                                            @Qualifier("serviceTokenAuthenticationManager")
                                            AuthenticationManager serviceTokens,
                                            SessionCookies sessionCookies,
                                            SessionProperties sessionProperties) {
        AuthenticationManager userManager = userTokens(jwtDecoder);
        http
                .csrf(csrf -> csrf.spa()
                        .csrfTokenRepository(csrfTokenRepository(sessionCookies.csrfCookieName(),
                                sessionProperties.secureCookies()))
                        // Muaf: çerez kullanmayan uçlar ve tarayıcının kendiliğinden göndermediği Authorization başlığı.
                        // Çerezle gelen kimlik CsrfFilter'dan SONRA AccessTokenCookieFilter ile Bearer'a çevrilir.
                        .ignoringRequestMatchers(COOKIELESS_ENDPOINTS, BEARER_HEADER)) // NOSONAR java:S4502
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(ADMIN_ENDPOINTS).hasRole(RoleCode.ADMIN.name())
                        .requestMatchers(PATHS.matcher(HttpMethod.GET, AuthApiPaths.INTERNAL_ACCOUNT))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.ACCOUNT_READ_SCOPE)
                        .requestMatchers(PATHS.matcher(HttpMethod.POST, AuthApiPaths.INTERNAL_ACCOUNT_STATUS))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.ACCOUNT_STATUS_WRITE_SCOPE)
                        .requestMatchers(PATHS.matcher(HttpMethod.POST, AuthApiPaths.INTERNAL_ACCOUNT_ROLES_GRANT),
                                PATHS.matcher(HttpMethod.POST, AuthApiPaths.INTERNAL_ACCOUNT_ROLES_REVOKE))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.ACCOUNT_ROLE_WRITE_SCOPE)
                        .requestMatchers(AUTHENTICATED_ENDPOINTS).authenticated()
                        .anyRequest().denyAll())
                // Cookie → Bearer çevirisi CSRF denetiminden sonra yapılır (bkz. AccessTokenResolver).
                .addFilterAfter(new AccessTokenCookieFilter(sessionCookies.accessCookieName(),
                        new OrRequestMatcher(PUBLIC_ENDPOINTS, INTERNAL_ENDPOINTS)),
                        CsrfFilter.class)
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(new AccessTokenResolver(PUBLIC_ENDPOINTS))
                        .authenticationManagerResolver(request -> INTERNAL_ENDPOINTS.matches(request)
                                ? serviceTokens : userManager)
                        .authenticationEntryPoint(problemDetailSecurityHandler)
                        .accessDeniedHandler(problemDetailSecurityHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailSecurityHandler)
                        .accessDeniedHandler(problemDetailSecurityHandler));
        return http.build();
    }

    @Bean
    ProblemDetailSecurityHandler problemDetailSecurityHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        return new ProblemDetailSecurityHandler(exceptionResolver);
    }

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 2);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilter(
            RequestLimitProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        FilterRegistrationBean<RequestBodySizeLimitFilter> registration = new FilterRegistrationBean<>(
                new RequestBodySizeLimitFilter(properties.maxBodySize().toBytes(), exceptionResolver));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }

    /** Kullanıcı access token'ları ({@code at+jwt}): {@code roles} claim'i {@code ROLE_} önekiyle yetkiye çevrilir. */
    private static AuthenticationManager userTokens(JwtDecoder jwtDecoder) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
        return new ProviderManager(provider);
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenClaims.ROLES);
        authorities.setAuthorityPrefix(ROLE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * SPA için JavaScript'in okuyabildiği CSRF cookie'si (gizli değildir; değeri X-XSRF-TOKEN başlığıyla geri gelir).
     * Secure açıkken __Host- öneki, alt alan adlarından cookie yerleştirilerek double-submit'in atlatılmasını engeller.
     */
    private static CookieCsrfTokenRepository csrfTokenRepository(String cookieName, boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse(); // NOSONAR java:S3330 - double-submit için SPA okumalı
        repository.setCookieName(cookieName);
        repository.setCookiePath(CSRF_COOKIE_PATH);
        repository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite(CSRF_COOKIE_SAME_SITE));
        return repository;
    }
}
