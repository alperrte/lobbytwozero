package com.ltz.game.infrastructure.security;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class GameSecurityConfiguration {
    @Bean
    SecurityFilterChain gameSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/game/games", "/api/v1/game/games/search", "/api/v1/game/games/popular").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/game/*/metadata", "/api/v1/game/*/card", "/api/v1/game/games/*", "/api/v1/game/catalog", "/api/v1/game/catalog/status").permitAll()
                .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(response, 401, "Unauthorized"))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(response, 403, "Forbidden")))
                .build();
    }

    private void writeProblem(jakarta.servlet.http.HttpServletResponse response, int status, String title)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + "}");
    }
}
