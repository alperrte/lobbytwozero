package com.ltz.event.infrastructure.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.ltz.event.api.controller.EventController;
import com.ltz.event.api.controller.InternalApiPaths;
import com.ltz.event.infrastructure.config.JwtConfig;

/**
 * Deny-by-default. Yalnız actuator health/info anonimdir; geri kalan her uç kimlik ister. Hangi token türünün kabul
 * edileceği YOLA göre seçilir, böylece bir tür diğerinin yerine geçemez:
 * <ul>
 *   <li>{@code /api/v1/event/**}: Auth'un kullanıcı access token'ı ({@code at+jwt}, {@code aud=ltz-api}). Etkinlik
 *       oluşturma yalnız {@link EventRoles#EVENT_CREATORS} rollerine açıktır.</li>
 *   <li>{@code /internal/**}: Auth'un servis token'ı ({@code svc+jwt}, {@code aud=event-service}) ve {@code account:erase}
 *       kapsamı; tanımsız her iç yol reddedilir.</li>
 * </ul>
 * Token'lar JWKS ile bu serviste doğrulanır ({@link JwtConfig}); Gateway'in kimlik başlıklarına güvenilmez. Yetkisiz rol
 * 403, kimliksiz istek 401 (ProblemDetail) döner. Tarayıcı çerezi → Bearer çevirisi ve CSRF/Origin denetimi Gateway'dedir
 * (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §6.1); resource server yapılandırması yalnız Bearer başlıklı istekleri
 * CSRF'ten muaf tutar.
 */
@Configuration
@Import(JwtConfig.class)
public class SecurityConfig {

	private static final String SCOPE_PREFIX = "SCOPE_";
	private static final String ROLE_PREFIX = "ROLE_";

	private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

	/** Servisler arası uçlar: yalnız servis token'ı kabul edilir. */
	static final RequestMatcher INTERNAL_ENDPOINTS = PATHS.matcher(InternalApiPaths.BASE + "/**");

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemDetailHandler,
			@Qualifier("userTokenDecoder") JwtDecoder userTokenDecoder,
			@Qualifier("serviceTokenDecoder") JwtDecoder serviceTokenDecoder) {
		AuthenticationManager userTokens = jwtManager(userTokenDecoder, converter(TokenClaims.ROLES, ROLE_PREFIX));
		AuthenticationManager serviceTokens = jwtManager(serviceTokenDecoder,
				converter(TokenClaims.SCOPE, SCOPE_PREFIX));
		http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers(PATHS.matcher(HttpMethod.DELETE, InternalApiPaths.ACCOUNT_ERASURE))
								.hasAuthority(SCOPE_PREFIX + TokenClaims.ACCOUNT_ERASE_SCOPE)
						.requestMatchers(INTERNAL_ENDPOINTS).denyAll()
						.requestMatchers(HttpMethod.POST, EventController.BASE_PATH)
								.hasAnyRole(EventRoles.EVENT_CREATORS.toArray(String[]::new))
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(problemDetailHandler)
						.accessDeniedHandler(problemDetailHandler))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.authenticationManagerResolver(
								request -> INTERNAL_ENDPOINTS.matches(request) ? serviceTokens : userTokens)
						.authenticationEntryPoint(problemDetailHandler)
						.accessDeniedHandler(problemDetailHandler));
		return http.build();
	}

	private static AuthenticationManager jwtManager(JwtDecoder decoder, JwtAuthenticationConverter converter) {
		JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
		provider.setJwtAuthenticationConverter(converter);
		return new ProviderManager(provider);
	}

	private static JwtAuthenticationConverter converter(String claim, String prefix) {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(claim);
		authorities.setAuthorityPrefix(prefix);
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}
}
