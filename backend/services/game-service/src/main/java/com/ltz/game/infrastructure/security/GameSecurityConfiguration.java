package com.ltz.game.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Herkese açık katalog okumaları dışındaki her şey Bearer JWT ister ve varsayılan olarak reddedilir.
 * Kimlik doğrulama yalnız Authorization başlığıyla yapılır (çerez kabul edilmez), bu yüzden token gerektiren
 * uçlarda CSRF ile korunacak bir ortam kimlik bilgisi yoktur; resource server bearer isteklerini CSRF'den muaf tutar.
 * Çerez tabanlı oturuma geçilirse CSRF tasarımı yeniden değerlendirilmelidir (docs-Game Service Auth belgesi).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class GameSecurityConfiguration {
    static final String ADMIN = "/api/v1/game/admin/**";
    static final String MODERATION = "/api/v1/game/moderation/**";
    static final String DEVELOPER = "/api/v1/game/developer/**";
    static final String FEEDBACK = "/api/v1/game/feedback/**";
    private static final String UNAUTHORIZED = "Unauthorized";

    @Bean
    SecurityFilterChain gameSecurity(HttpSecurity http, JwtDecoder decoder, LtzJwtAuthenticationConverter converter, RateLimitProperties rateLimit) {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher batch = paths.matcher(HttpMethod.POST, "/api/v1/game/games/batch");
        RequestMatcher tokenProtected = new OrRequestMatcher(paths.matcher(ADMIN), paths.matcher(MODERATION), paths.matcher(DEVELOPER), paths.matcher(FEEDBACK));
        var limiter = new TokenBucketRateLimiter(rateLimit.batch().capacity(), rateLimit.batch().refillPerSecond(), rateLimit.maxKeys(), System::nanoTime);
        var rateLimitFilter = new BatchRateLimitFilter(batch, limiter, new ClientAddressResolver(rateLimit.trustedProxies()));
        var privateFeedback=new OrRequestMatcher(paths.matcher(FEEDBACK),paths.matcher("/api/v1/game/developer/games/*/feedback/**"));
        var feedbackFilter=new FeedbackPrivacyAndRateLimitFilter(privateFeedback,paths.matcher(HttpMethod.POST,"/api/v1/game/feedback"),
                rateLimit.maxKeys(),new ClientAddressResolver(rateLimit.trustedProxies()));
        return http
                .addFilterAfter(feedbackFilter,org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
                // Anonim toplu okuma istemci başına sınırlanır (429); sınır CSRF/yetki işleminden önce uygulanır.
                .addFilterBefore(rateLimitFilter, CsrfFilter.class)
                // Varsayılan CSRF koruması açıktır; yalnız anonim toplu okuma filtreyle açıkça atlanır (bkz. sınıf açıklaması).
                .addFilterBefore(new PublicBatchCsrfExemptionFilter(batch), CsrfFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/game/games", "/api/v1/game/games/search", "/api/v1/game/games/popular").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/game/*/metadata", "/api/v1/game/*/card", "/api/v1/game/games/*", "/api/v1/game/catalog", "/api/v1/game/catalog/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/game/games/*/developer-content").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/game/games/*/modules/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/game/games/batch").permitAll()
                        .requestMatchers(ADMIN).hasRole("ADMIN")
                        .requestMatchers(MODERATION).hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(DEVELOPER).hasRole("GAME_DEVELOPER")
                        .requestMatchers(FEEDBACK).authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(response, 401, UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> denied(request, response, exception, tokenProtected)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(response, 401, UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> denied(request, response, exception, tokenProtected)))
                .build();
    }

    /** Token olmadan gelen korumalı yazma isteği CSRF filtresine takılsa da kimliksiz (401) sayılır. */
    private void denied(HttpServletRequest request, HttpServletResponse response, Exception exception, RequestMatcher tokenProtected)
            throws IOException {
        boolean anonymousProtected = exception instanceof CsrfException && request.getHeader("Authorization") == null
                && tokenProtected.matches(request);
        if (anonymousProtected) writeProblem(response, 401, UNAUTHORIZED);
        else writeProblem(response, 403, "Forbidden");
    }

    private void writeProblem(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + "}");
    }
}
