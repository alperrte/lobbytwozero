package com.ltz.recommendation.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecommendationAuthProperties.class)
public class RecommendationSecurityConfiguration {
    @Bean
    SecurityFilterChain recommendationSecurity(HttpSecurity http, ObjectMapper mapper,
            @Qualifier("recommendationUserDecoder") JwtDecoder userDecoder,
            @Qualifier("recommendationServiceDecoder") JwtDecoder serviceDecoder) {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        var users = new JwtAuthenticationProvider(userDecoder);
        users.setJwtAuthenticationConverter(converter);
        var userManager = new ProviderManager(users);
        var services = new ProviderManager(new JwtAuthenticationProvider(serviceDecoder));
        http.httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new RecommendationBodyLimitFilter(mapper), CsrfFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/internal/v1/accounts/*")
                        .hasAuthority("SCOPE_account:erase")
                        .requestMatchers("/api/v1/recommendation/games", "/api/v1/recommendation/hardware/assessments",
                                "/api/v1/recommendation/assistant", "/api/v1/recommendation/players",
                                "/api/v1/recommendation/friends", "/api/v1/recommendation/games/hardware-compatible",
                                "/api/v1/recommendation/runs",
                                "/api/v1/recommendation/runs/**")
                        .hasAnyRole("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.authenticationManagerResolver(request ->
                        request.getRequestURI().startsWith("/internal/") ? services : userManager)
                        .authenticationEntryPoint((request, response, error) -> problem(response, mapper, 401))
                        .accessDeniedHandler((request, response, error) -> problem(response, mapper, 403)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> problem(response, mapper, 401))
                        .accessDeniedHandler((request, response, error) -> problem(response, mapper, 403)));
        // Bearer is exempted by resource-server; cookie CSRF/Origin is checked by Gateway before translation.
        return http.build();
    }

    @Bean("recommendationUserDecoder")
    JwtDecoder userDecoder(RecommendationAuthProperties properties, JWKSource<SecurityContext> source) {
        return decoder(properties, source, false);
    }

    @Bean("recommendationServiceDecoder")
    JwtDecoder serviceDecoder(RecommendationAuthProperties properties, JWKSource<SecurityContext> source) {
        return decoder(properties, source, true);
    }

    private static JwtDecoder decoder(RecommendationAuthProperties properties, JWKSource<SecurityContext> source, boolean service) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri().toString()).validateType(false)
                .jwtProcessorCustomizer(processor -> processor.setJWSKeySelector(
                        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source))).build();
        decoder.setClaimSetConverter(RecommendationJwtValidators::requiredRawClaims);
        decoder.setJwtValidator(RecommendationJwtValidators.validator(properties.issuer(),
                service ? "recommendation-service" : properties.audience(), service, Clock.systemUTC()));
        return token -> {
            try { return decoder.decode(token); }
            catch (org.springframework.security.oauth2.jwt.JwtException _) {
                throw new org.springframework.security.oauth2.jwt.BadJwtException("Token validation failed");
            }
        };
    }

    private static void problem(HttpServletResponse response, ObjectMapper mapper, int status) throws IOException {
        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setProperty("code", status == 401 ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED");
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "private, no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(response.getOutputStream(), body);
    }
}
