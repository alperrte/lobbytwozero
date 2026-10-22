package com.ltz.community.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny-by-default: yalnız actuator health/info ve herkese açık gönderi okuma uçları (GET) anonimdir. Kimlik doğrulama Bearer JWT ile yapılır;
 * JWT doğrulama anahtarı sözleşmesi (auth-service) netleşene kadar decoder fail-closed çalışır.
 * CSRF koruması varsayılan olarak açıktır; resource server yapılandırması yalnız Bearer başlıklı istekleri muaf tutar
 * (başka bir site bu başlığı CORS izni olmadan ekleyemez). Çerez tabanlı oturuma geçilirse tasarım yeniden değerlendirilmelidir.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			ProblemDetailSecurityHandler problemDetailHandler) {
		http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
						// Misafir okuması (kullanıcı kararı): akış, detay ve görsel içeriği; servis katmanı yalnız VISIBLE gönderiyi (veya
						// sahibine kendi gizli gönderisini) verir. Yazma uçları kimlik ister.
						.requestMatchers(HttpMethod.GET, "/api/v1/community/posts", "/api/v1/community/posts/*",
								"/api/v1/community/posts/*/images/*", "/api/v1/community/users/*/posts").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(problemDetailHandler)
						.accessDeniedHandler(problemDetailHandler))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.authenticationEntryPoint(problemDetailHandler)
						.accessDeniedHandler(problemDetailHandler)
						.jwt(Customizer.withDefaults()));
		return http.build();
	}

	/**
	 * Geçici fail-closed decoder: hiçbir token kabul edilmez (401). Gerçek doğrulama anahtarı/JWKS
	 * yapılandırması auth-service sözleşmesiyle birlikte bu bean'in yerine geçer.
	 */
	@Bean
	public JwtDecoder jwtDecoder() {
		return token -> {
			throw new BadJwtException("JWT verification is not configured");
		};
	}
}
