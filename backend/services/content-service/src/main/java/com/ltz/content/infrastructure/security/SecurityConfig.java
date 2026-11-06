package com.ltz.content.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.ltz.content.infrastructure.config.JwtConfig;

/**
 * Deny-by-default. Anonim yalnız actuator health/info ve herkese açık okuma uçlarıdır (içerik akışı, detay, görsel, kategori
 * listesi; hepsi GET ve yalnız yayımlanmış içerik); geri kalan her uç Auth'un kullanıcı access token'ını
 * ({@code at+jwt}, {@code aud=ltz-api}) ister.
 * {@code /internal/**} altında henüz uç yoktur ve tamamı reddedilir. Token JWKS ile bu serviste doğrulanır
 * ({@link JwtConfig}); Gateway'in kimlik başlıklarına güvenilmez. CSRF koruması varsayılan olarak açıktır; resource server
 * yapılandırması yalnız Bearer başlıklı istekleri muaf tutar. Tarayıcı çerezi → Bearer çevirisi ve CSRF/Origin denetimi
 * Gateway'dedir (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §6.1).
 */
@Configuration
@Import(JwtConfig.class)
public class SecurityConfig {

	private static final String ROLE_PREFIX = "ROLE_";

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemDetailHandler,
			JwtDecoder userTokenDecoder) {
		http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/internal/**").denyAll()
						// Herkese açık okuma (ürün kararı, CONT-02): akış, detay, görsel ve kategori listesi (GET). Servis katmanı yalnız
						// YAYIMLANMIŞ ve silinmemiş içeriği (veya sahibine kendi içeriğini) verir; taslak başkası için 404'tür. Yazma uçları
						// ve diğer her şey kimlik ister.
						.requestMatchers(HttpMethod.GET, "/api/v1/content/contents", "/api/v1/content/contents/*",
								"/api/v1/content/contents/*/images/*", "/api/v1/content/categories").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(problemDetailHandler)
						.accessDeniedHandler(problemDetailHandler))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.decoder(userTokenDecoder).jwtAuthenticationConverter(roleConverter()))
						.authenticationEntryPoint(problemDetailHandler)
						.accessDeniedHandler(problemDetailHandler));
		return http.build();
	}

	/** Kullanıcı token'ı: {@code roles} listesi {@code ROLE_} önekiyle yetkiye çevrilir. */
	private static JwtAuthenticationConverter roleConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(TokenClaims.ROLES);
		authorities.setAuthorityPrefix(ROLE_PREFIX);
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}
}
