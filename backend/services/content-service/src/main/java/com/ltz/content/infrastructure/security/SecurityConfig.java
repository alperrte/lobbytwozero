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
 * Deny-by-default. Yalnız actuator health/info anonimdir; geri kalan her uç Auth'un kullanıcı access token'ını
 * ({@code at+jwt}, {@code aud=ltz-api}) ister (bu fazda genel okuma ucu yoktur; herkese açık haber akışı sonraki fazdadır).
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
