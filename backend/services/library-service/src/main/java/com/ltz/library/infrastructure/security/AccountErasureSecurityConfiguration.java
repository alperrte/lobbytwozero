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
public class AccountErasureSecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, @Qualifier("serviceJwtDecoder") JwtDecoder decoder) {
        var paths = PathPatternRequestMatcher.withDefaults();
        var internal = paths.matcher("/internal/**");
        var provider = new JwtAuthenticationProvider(decoder); // yalnız scope → SCOPE_*, roles okunmaz
        var scopes = new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("scope"); // scp fallback'ı bu sözleşmede yetki vermez
        var converter = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        provider.setJwtAuthenticationConverter(converter);
        var serviceTokens = new ProviderManager(provider);
        var rejectTokens = new ProviderManager(new JwtAuthenticationProvider(
                token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("Kullanıcı uçları henüz uygulanmadı."); }));
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.DELETE, AccountErasureController.PATH).hasAuthority("SCOPE_account:erase")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .authenticationManagerResolver(request -> internal.matches(request) ? serviceTokens : rejectTokens)
                        .authenticationEntryPoint((request, response, error) -> writeProblem(response, 401))
                        .accessDeniedHandler((request, response, error) -> writeProblem(response, 403)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> writeProblem(response, 401))
                        .accessDeniedHandler((request, response, error) -> writeProblem(response,
                                internal.matches(request) && request.getHeader("Authorization") == null ? 401 : 403)))
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
