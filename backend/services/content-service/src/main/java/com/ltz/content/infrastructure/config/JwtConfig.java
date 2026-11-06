package com.ltz.content.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.ltz.content.infrastructure.security.TokenClaims;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;

/**
 * Kullanıcı token'ını Auth Service'in JWKS anahtarıyla bu serviste yeniden doğrular (Gateway'e güvenilmez). Yalnız RS256
 * kabul edilir ({@code alg=none} ve HS256 reddedilir); {@code typ=at+jwt}, {@code iss}, {@code aud} ve {@code exp}/{@code nbf}
 * /{@code iat}/{@code sub} zorunludur (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3.2). Servis token'ları ({@code svc+jwt})
 * bu serviste kabul edilmez: şimdilik içeride servis kimlikli uç yoktur. JWKS ilk doğrulamada çekilir ve önbelleğe alınır
 * (bilinmeyen {@code kid} için yenilenir).
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

	/** Küçük saat sapması toleransı (rehber: en fazla 60 sn). */
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

	/** Auth'un her kullanıcı token'ında bulunan, ham JWT'de zorunlu tutulan alanlar. */
	private static final Set<String> REQUIRED_CLAIMS = Set.of("exp", "nbf", "iat", "sub");

	@Bean
	JwtDecoder userTokenDecoder(@Value("${ltz.content.auth.jwks-uri}") String jwksUri,
			@Value("${ltz.content.auth.issuer}") String issuer,
			@Value("${ltz.content.auth.audience}") String audience) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				// typ, doğrulayıcı içinde açıkça denetlenir (at+jwt).
				.validateType(false)
				// Zorunlu alanı bulunmayan token imza doğrulamasından hemen sonra, Spring bu alanları kendiliğinden
				// türetmeden (iat yoksa exp-1 sn atar) ham haliyle reddedilir.
				.jwtProcessorCustomizer(processor -> processor.setJWTClaimsSetVerifier(
						new DefaultJWTClaimsVerifier<>(null, REQUIRED_CLAIMS)))
				.build();
		decoder.setJwtValidator(accessTokenValidator(issuer, audience, Clock.systemUTC()));
		return decoder;
	}

	/** İmza dışındaki tüm kullanıcı token kuralları; JWKS'siz de test edilebilsin diye ayrıdır. */
	public static OAuth2TokenValidator<Jwt> accessTokenValidator(String issuer, String audience, Clock clock) {
		JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
		timestamps.setClock(clock);
		return new DelegatingOAuth2TokenValidator<>(List.of(
				timestamps,
				new JwtIssuerValidator(issuer),
				new JwtTypeValidator(TokenClaims.ACCESS_TOKEN_TYPE),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
						tokenAudience -> tokenAudience != null && tokenAudience.contains(audience)),
				new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
				new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull),
				new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull)));
	}
}
