package com.ltz.content.infrastructure.security;

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

import com.ltz.content.api.controller.InternalApiPaths;
import com.ltz.content.infrastructure.config.JwtConfig;

/**
 * Deny-by-default. Anonim yalnız actuator health/info ve herkese açık okuma uçlarıdır (içerik akışı, detay, görsel, kategori
 * listesi; hepsi GET ve yalnız yayımlanmış içerik); geri kalan her uç kimlik ister. Hangi token türünün kabul edileceği
 * YOLA göre seçilir, böylece bir tür diğerinin yerine geçemez:
 * <ul>
 *   <li>{@code /api/v1/content/**}: Auth'un kullanıcı access token'ı ({@code at+jwt}, {@code aud=ltz-api}).</li>
 *   <li>{@code /internal/**}: Auth'un imzaladığı servis token'ı ({@code svc+jwt}, {@code aud=content-service}) ve ucun
 *       istediği kapsam ({@code events:ingest}); tanımsız her iç yol reddedilir.</li>
 * </ul>
 * Token'lar JWKS ile bu serviste doğrulanır ({@link JwtConfig}); Gateway'in kimlik başlıklarına güvenilmez. CSRF koruması
 * varsayılan olarak açıktır; resource server yapılandırması yalnız Bearer başlıklı istekleri muaf tutar. Tarayıcı çerezi →
 * Bearer çevirisi ve CSRF/Origin denetimi Gateway'dedir (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §6.1).
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
		AuthenticationManager serviceTokens = jwtManager(serviceTokenDecoder, converter(TokenClaims.SCOPE, SCOPE_PREFIX));
		http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
						// Servisler arası uçlar (Gateway'e açılmaz): her biri kendi kapsamını ister, kalanı reddedilir.
						.requestMatchers(PATHS.matcher(HttpMethod.POST, InternalApiPaths.EVENTS))
								.hasAuthority(SCOPE_PREFIX + TokenClaims.EVENT_INGEST_SCOPE)
						.requestMatchers(INTERNAL_ENDPOINTS).denyAll()
						// Herkese açık okuma (ürün kararı, CONT-02): akış, detay, görsel ve kategori listesi (GET). Servis katmanı yalnız
						// YAYIMLANMIŞ ve silinmemiş içeriği (veya sahibine kendi içeriğini) verir; taslak ve kaldırılmış içerik başkası
						// için 404'tür. Yazma uçları ve moderasyon uçları kimlik ister (rol servis katmanında denetlenir).
						.requestMatchers(HttpMethod.GET, "/api/v1/content/contents", "/api/v1/content/contents/*",
								"/api/v1/content/contents/*/images/*", "/api/v1/content/categories").permitAll()
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

	/**
	 * Kullanıcı token'ı: {@code roles} listesi {@code ROLE_} önekiyle; servis token'ı: {@code scope} değeri {@code SCOPE_}
	 * önekiyle yetkiye çevrilir.
	 */
	private static JwtAuthenticationConverter converter(String claim, String prefix) {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(claim);
		authorities.setAuthorityPrefix(prefix);
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}
}
