package com.ltz.user.infrastructure.config;

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

import com.ltz.user.api.controller.InternalApiPaths;
import com.ltz.user.api.controller.UserApiPaths;
import com.ltz.user.infrastructure.security.RequestBodySizeLimitFilter;
import com.ltz.user.infrastructure.security.TokenClaims;

import jakarta.servlet.DispatcherType;

/**
 * Fail-closed güvenlik zinciri: yalnız sağlık ucu herkese açıktır; User uçları geçerli kullanıcı token'ı
 * ({@code at+jwt}), iç uçlar ({@code /internal/**}) geçerli servis token'ı ({@code svc+jwt}) ister; geri kalan her şey
 * reddedilir. Hangi token türünün kabul edileceği yola göre seçilir, böylece bir tür diğerinin yerine geçemez.
 * Kimlik yalnız {@code Authorization: Bearer} başlığından okunur; servis çerez okumaz ve oturum açmaz. Tarayıcı çerezi →
 * Bearer çevirisi ve CSRF/Origin denetimi Gateway'de yapılır (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §6.1), bu yüzden
 * bu zincirde CSRF filtresi gereksizdir. 401/403 yanıtları @RestControllerAdvice'a devredilir; ProblemDetail döner.
 *
 * <p>Faz 2'de herkese açık profil okuma (misafir) uçları eklenecek; onlar da burada açıkça listelenecektir.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String HEALTH = "/actuator/health";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /** Kimlik gerektirmeyen uçlar. */
    static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.GET, HEALTH),
            PATHS.matcher(HttpMethod.GET, HEALTH + "/**"));

    /** Geçerli kullanıcı token'ı isteyen uçlar (tüm kayıtlı oyuncular; sahiplik kontrolü servis katmanındadır). */
    static final RequestMatcher AUTHENTICATED_ENDPOINTS = PATHS.matcher(UserApiPaths.BASE + "/**");

    /** Servisler arası uçlar: yalnız servis token'ı kabul edilir. */
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
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(PATHS.matcher(HttpMethod.DELETE, InternalApiPaths.ACCOUNT_ERASURE))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.ACCOUNT_ERASE_SCOPE)
                        .requestMatchers(PATHS.matcher(HttpMethod.POST, InternalApiPaths.EVENTS))
                                .hasAuthority(SCOPE_PREFIX + TokenClaims.EVENTS_INGEST_SCOPE)
                        .requestMatchers(AUTHENTICATED_ENDPOINTS).authenticated()
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

    /** Kullanıcı token'ı: {@code roles} listesi {@code ROLE_} önekiyle yetkiye çevrilir. */
    private static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenClaims.ROLES);
        roles.setAuthorityPrefix(ROLE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    /** Servis token'ı: {@code scope} değeri {@code SCOPE_} önekiyle yetkiye çevrilir (ör. SCOPE_account:erase). */
    private static JwtAuthenticationConverter scopeConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("scope");
        scopes.setAuthorityPrefix(SCOPE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        return converter;
    }
}
