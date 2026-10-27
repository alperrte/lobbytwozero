package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.AuthenticatedActor;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class HardwareSecurityConfiguration {
    private static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";
    private static final org.springframework.security.web.util.matcher.RequestMatcher ACCOUNT_ERASURE =
            org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults()
                    .matcher(org.springframework.http.HttpMethod.DELETE, "/internal/v1/accounts/*");
    @Bean
    SecurityFilterChain hardwareSecurity(HttpSecurity http, ObjectMapper mapper,
            @Qualifier("hardwareUserDecoder") JwtDecoder decoder,
            @Qualifier("hardwareServiceDecoder") JwtDecoder serviceDecoder, HardwareJwtAuthenticationConverter converter) {
        var userProvider = new JwtAuthenticationProvider(decoder);
        userProvider.setJwtAuthenticationConverter(converter);
        var userManager = new ProviderManager(userProvider);
        var serviceManager = new ProviderManager(new JwtAuthenticationProvider(serviceDecoder));
        // Cookie authentication is owned by Gateway; Hardware only resolves Bearer tokens.
        http.httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new HardwareRequestBodyLimitFilter(mapper), CsrfFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Existing readiness/liveness contract only; no business data.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(ACCOUNT_ERASURE)
                        .hasAuthority("SCOPE_account:erase")
                        .requestMatchers("/api/v1/hardware/profiles/*/sync/snapshots")
                        .access((authentication, context) -> deviceAccess(authentication.get()))
                        .requestMatchers("/api/v1/hardware/moderation/reviews/**")
                        .access((authentication, context) -> {
                            var auth = authentication.get();
                            if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedActor)) {
                                return new AuthorizationDecision(false);
                            }
                            return new AuthorizationDecision(auth.getAuthorities().stream().anyMatch(a ->
                                    com.ltz.hardware.application.port.HardwareReviewModerationAccess.PERMISSION.equals(a.getAuthority())));
                        })
                        .requestMatchers("/api/v1/hardware/profiles", "/api/v1/hardware/profiles/**",
                                "/api/v1/hardware/comparisons/**", "/api/v1/hardware/reviews/**",
                                "/api/v1/hardware/catalog/products/*/reviews", "/api/v1/hardware/review-reports/**")
                        .access((authentication, context) -> {
                            var auth = authentication.get();
                            return new AuthorizationDecision(auth != null && auth.isAuthenticated()
                                    && auth.getPrincipal() instanceof AuthenticatedActor);
                        })
                        .requestMatchers("/api/v1/hardware/catalog/**", "/api/v1/hardware/campaigns",
                                "/api/v1/hardware/campaigns/**")
                        .access((authentication, context) -> catalogAccess(authentication.get(),
                                context.getRequest().getMethod()))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.authenticationManagerResolver(request ->
                        request.getRequestURI().startsWith("/internal/", request.getContextPath().length())
                                ? serviceManager : userManager)
                        .authenticationEntryPoint((request, response, error) ->
                                writeProblem(response, mapper, 401, AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, error) ->
                                writeProblem(response, mapper, 403, "ACCESS_DENIED")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                writeProblem(response, mapper, 401, AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, error) -> {
                            // Missing Bearer on D4 is an authentication failure. CSRF still rejects the request;
                            // this only gives the required 401 and never forwards it to the erasure controller.
                            boolean missingServiceToken = error instanceof org.springframework.security.web.csrf.CsrfException
                                    && ACCOUNT_ERASURE.matches(request);
                            writeProblem(response, mapper, missingServiceToken ? 401 : 403,
                                    missingServiceToken ? AUTHENTICATION_REQUIRED : "ACCESS_DENIED");
                        }));
        // CSRF remains enabled. Resource server exempts explicit Bearer requests;
        // Gateway enforces double-submit + Origin before converting browser cookies.
        return http.build();
    }

    private static AuthorizationDecision deviceAccess(org.springframework.security.core.Authentication auth) {
        boolean device = auth != null && auth.isAuthenticated() && auth.getPrincipal()
                instanceof com.ltz.hardware.application.port.AuthenticatedHardwareDevice;
        return new AuthorizationDecision(device && auth.getAuthorities().stream().anyMatch(a ->
                com.ltz.hardware.application.port.HardwareSyncDeviceAccess.PERMISSION.equals(a.getAuthority())));
    }

    private static AuthorizationDecision catalogAccess(org.springframework.security.core.Authentication auth, String method) {
        boolean trusted = auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedActor;
        boolean read = method.equals("GET");
        return new AuthorizationDecision(trusted && (read || auth.getAuthorities().stream()
                .anyMatch(a -> com.ltz.hardware.application.port.CatalogWriteAccess.PERMISSION.equals(a.getAuthority()))));
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper mapper,
                                     int status, String code) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),
                status == 401 ? "Doğrulanmış kimlik gerekli." : "Bu işlem için erişim reddedildi.");
        problem.setProperty("code", code);
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(mapper.writeValueAsString(problem));
    }
}
