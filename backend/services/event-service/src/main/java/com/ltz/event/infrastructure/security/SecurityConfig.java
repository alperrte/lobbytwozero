package com.ltz.event.infrastructure.security;

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

import com.ltz.event.api.controller.EventController;
import com.ltz.event.infrastructure.config.JwtConfig;

/**
 * Deny-by-default. Yalnız actuator health/info anonimdir; geri kalan her uç kimlik ister. Kullanıcı kimliği Auth'un
 * access token'ından ({@code at+jwt}, {@code aud=ltz-api}) JWKS ile bu serviste doğrulanır ({@link JwtConfig});
 * Gateway'in kimlik başlıklarına güvenilmez. Etkinlik oluşturma yalnız {@link EventRoles#EVENT_CREATORS} rollerine
 * açıktır; yetkisiz rol 403, kimliksiz istek 401 (ProblemDetail) döner. Tarayıcı çerezi → Bearer çevirisi ve CSRF/Origin
 * denetimi Gateway'dedir (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §6.1); resource server yapılandırması yalnız
 * Bearer başlıklı istekleri CSRF'ten muaf tutar.
 *
 * <p>{@code /internal/**} yolları bu serviste henüz tanımlı değildir (EVT-24) ve herkese kapalıdır.
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
						.requestMatchers(HttpMethod.POST, EventController.BASE_PATH)
								.hasAnyRole(EventRoles.EVENT_CREATORS.toArray(String[]::new))
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
