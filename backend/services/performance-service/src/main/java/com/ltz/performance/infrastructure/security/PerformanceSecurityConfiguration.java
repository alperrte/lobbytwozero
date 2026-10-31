package com.ltz.performance.infrastructure.security;

import com.ltz.performance.application.port.AuthenticatedActor;
import com.ltz.performance.application.port.AuthenticatedPerformanceDevice;
import com.ltz.performance.application.port.PerformanceSyncDeviceAccess;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

/**
 * Varsayılan reddet (deny-by-default). Üç ayrı kimlik doğrulama yöneticisi vardır: kullanıcı (at+jwt),
 * servis (svc+jwt, yalnız /internal) ve cihaz (dev+jwt, yalnız ölçüm gönderimi). Hiçbir yol için
 * permitAll yoktur; sağlık uçları hariç.
 */
@Configuration
public class PerformanceSecurityConfiguration {
    private static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";
    private static final String SYNC_BATCHES_PATH = "/api/v1/performance/sync/batches";
    private static final RequestMatcher ACCOUNT_ERASURE = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.DELETE, "/internal/v1/accounts/*");
    private static final RequestMatcher DEVICE_VERIFY = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.GET, "/internal/v1/accounts/*/devices/*");
    private static final RequestMatcher SYNC_BATCHES = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, SYNC_BATCHES_PATH);

    @Bean
    SecurityFilterChain performanceSecurity(HttpSecurity http, ObjectMapper mapper,
            @Qualifier("performanceUserDecoder") JwtDecoder userDecoder,
            @Qualifier("performanceServiceDecoder") JwtDecoder serviceDecoder,
            @Qualifier("performanceDeviceDecoder") JwtDecoder deviceDecoder,
            PerformanceJwtAuthenticationConverter converter) {
        var userProvider = new JwtAuthenticationProvider(userDecoder);
        userProvider.setJwtAuthenticationConverter(converter);
        var userManager = new ProviderManager(userProvider);
        var serviceManager = new ProviderManager(new JwtAuthenticationProvider(serviceDecoder));
        var deviceProvider = new JwtAuthenticationProvider(deviceDecoder);
        deviceProvider.setJwtAuthenticationConverter(new PerformanceDeviceJwtAuthenticationConverter());
        var deviceManager = new ProviderManager(deviceProvider);
        // Cookie kimlik doğrulaması Gateway'in işidir; Performance yalnız Bearer çözer.
        http.httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new PerformanceRequestBodyLimitFilter(mapper), CsrfFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(ACCOUNT_ERASURE).hasAuthority("SCOPE_account:erase")
                        .requestMatchers(DEVICE_VERIFY).hasAuthority("SCOPE_devices:verify")
                        .requestMatchers(SYNC_BATCHES)
                        .access((authentication, context) -> deviceAccess(authentication.get()))
                        .requestMatchers("/api/v1/performance/sync-consent", "/api/v1/performance/devices",
                                "/api/v1/performance/devices/*")
                        .access((authentication, context) -> actorAccess(authentication.get()))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.authenticationManagerResolver(request ->
                        request.getRequestURI().startsWith("/internal/", request.getContextPath().length())
                                ? serviceManager : SYNC_BATCHES.matches(request) ? deviceManager : userManager)
                        .authenticationEntryPoint((request, response, error) ->
                                writeProblem(response, mapper, 401, AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, error) ->
                                writeProblem(response, mapper, 403, "ACCESS_DENIED")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                writeProblem(response, mapper, 401, AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, error) -> {
                            // Bearer'lı istek CSRF'den muaftır; CSRF reddi yalnız Bearer'sız çağrıda oluşur ve
                            // oturum/cookie desteklenmediği için bu bir kimlik doğrulama hatasıdır (401).
                            boolean missingBearer = error instanceof CsrfException;
                            writeProblem(response, mapper, missingBearer ? 401 : 403,
                                    missingBearer ? AUTHENTICATION_REQUIRED : "ACCESS_DENIED");
                        }));
        // CSRF açık kalır; Bearer'lı istekler resource server tarafından muaf tutulur.
        return http.build();
    }

    private static AuthorizationDecision deviceAccess(Authentication auth) {
        boolean device = auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AuthenticatedPerformanceDevice;
        return new AuthorizationDecision(device && auth.getAuthorities().stream()
                .anyMatch(a -> PerformanceSyncDeviceAccess.PERMISSION.equals(a.getAuthority())));
    }

    private static AuthorizationDecision actorAccess(Authentication auth) {
        return new AuthorizationDecision(auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AuthenticatedActor);
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
