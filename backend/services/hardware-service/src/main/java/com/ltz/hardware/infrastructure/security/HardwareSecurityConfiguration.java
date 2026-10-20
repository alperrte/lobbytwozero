package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.AuthenticatedActor;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    @Bean
    SecurityFilterChain hardwareSecurity(HttpSecurity http, ObjectMapper mapper) throws Exception {
        // No production authenticator until the real Auth contract is agreed.
        // Client headers, cookies and bearer tokens cannot create an actor here.
        http.httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new HardwareRequestBodyLimitFilter(mapper), CsrfFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Existing readiness/liveness contract only; no business data.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/hardware/profiles", "/api/v1/hardware/profiles/**")
                        .access((authentication, context) -> {
                            var auth = authentication.get();
                            return new AuthorizationDecision(auth != null && auth.isAuthenticated()
                                    && auth.getPrincipal() instanceof AuthenticatedActor);
                        })
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                writeProblem(response, mapper, 401, "AUTHENTICATION_REQUIRED"))
                        .accessDeniedHandler((request, response, error) ->
                                writeProblem(response, mapper, 403, "ACCESS_DENIED")));
        // CSRF remains enabled. Cookie transport is not implemented or assumed.
        return http.build();
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
        response.getWriter().write(mapper.writeValueAsString(problem));
    }
}
