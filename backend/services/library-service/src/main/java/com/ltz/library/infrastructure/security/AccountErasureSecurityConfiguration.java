package com.ltz.library.infrastructure.security;

import com.ltz.library.api.controller.AccountErasureController;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(UserTokenDecoderConfiguration.class)
public class AccountErasureSecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, @Qualifier("serviceJwtDecoder") JwtDecoder decoder,
            @Qualifier("userJwtDecoder") JwtDecoder userDecoder,
            @org.springframework.beans.factory.annotation.Value("${springdoc.api-docs.enabled:false}") boolean swagger) {
        var paths = PathPatternRequestMatcher.withDefaults();
        var internal = paths.matcher("/internal/**");
        var provider = new JwtAuthenticationProvider(decoder); // yalnız scope → SCOPE_*, roles okunmaz
        var scopes = new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("scope"); // scp fallback'ı bu sözleşmede yetki vermez
        var converter = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        provider.setJwtAuthenticationConverter(converter);
        var serviceTokens = new ProviderManager(provider);
        var userProvider = new JwtAuthenticationProvider(userDecoder);
        var userConverter = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        userConverter.setJwtGrantedAuthoritiesConverter(UserTokenDecoderConfiguration::authorities);
        userProvider.setJwtAuthenticationConverter(userConverter);
        var userTokens = new ProviderManager(userProvider);
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.DELETE, AccountErasureController.PATH).hasAuthority("SCOPE_account:erase")
                        .requestMatchers(HttpMethod.GET, "/internal/v1/library/users/*/entries", "/internal/v1/library/users/*/entries/statistics")
                            .hasAuthority("SCOPE_library:profile:read")
                        .requestMatchers(HttpMethod.GET, "/internal/v1/library/users/*/recommendation-signals")
                            .hasAuthority("SCOPE_library:recommendation:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/library/users/*/entries", "/api/v1/library/users/*/entries/statistics")
                            .hasAnyRole("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/library/entries")
                            .hasAnyRole("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/library/entries/*")
                            .hasAnyRole("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/library/entries/*/favorite", "/api/v1/library/entries/*/completed")
                            .hasAnyRole("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/library/entries/*/favorite", "/api/v1/library/entries/*/completed")
                            .hasAnyRole("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/library/entries", "/api/v1/library/entries/statistics", "/api/v1/library/entries/favorites", "/api/v1/library/entries/completed")
                            .hasAnyRole("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(swagger))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .authenticationManagerResolver(request -> internal.matches(request) ? serviceTokens : userTokens)
                        .authenticationEntryPoint((request, response, error) -> writeProblem(response, 401))
                        .accessDeniedHandler((request, response, error) -> writeProblem(response, 403)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> writeProblem(response, 401))
                        .accessDeniedHandler((request, response, error) -> writeProblem(response,
                                (internal.matches(request) || request.getRequestURI().startsWith("/api/v1/library/"))
                                        && request.getHeader("Authorization") == null ? 401 : 403)))
                .build();
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse response, int status) throws java.io.IOException {
        response.setStatus(status);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + (status == 401 ? "Unauthorized" : "Forbidden")
                + "\",\"status\":" + status + "}");
    }
}
