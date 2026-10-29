package com.ltz.community.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.ltz.community.infrastructure.security.TokenClaims;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;

/**
 * Token'ları Auth Service'in JWKS anahtarıyla bu serviste yeniden doğrular (Gateway'e güvenilmez). Yalnız RS256 kabul
 * edilir ({@code alg=none} ve HS256 reddedilir). İki ayrı token türü vardır ve {@code typ} başlığıyla birbirinden ayrılır:
 * <ul>
 *   <li>Kullanıcı access token'ı ({@code at+jwt}): {@code /api/v1/community/**} uçları için.</li>
 *   <li>Servis token'ı ({@code svc+jwt}, D4 sözleşmesi): yalnız {@code /internal/**} uçları için.</li>
 * </ul>
 * Kullanıcı kuralları Auth'un kendi doğrulayıcısıyla aynıdır (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3.2); fazladan
 * kural eklenmez. JWKS, ilk doğrulamada Auth'tan çekilir ve önbelleğe alınır (bilinmeyen {@code kid} için yenilenir).
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

	/** Küçük saat sapması toleransı (rehber: en fazla 60 sn). */
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

	/** Auth'un her token'ında bulunan, ham JWT'de zorunlu tutulan alanlar (rehber §3.2). */
	private static final Set<String> REQUIRED_CLAIMS = Set.of("exp", "nbf", "iat", "sub");

	@Bean
	JwtDecoder userTokenDecoder(@Value("${ltz.community.auth.jwks-uri}") String jwksUri,
			@Value("${ltz.community.auth.issuer}") String issuer,
			@Value("${ltz.community.auth.audience}") String audience) {
		return decoder(jwksUri, accessTokenValidator(issuer, audience, Clock.systemUTC()));
	}

	@Bean
	JwtDecoder serviceTokenDecoder(@Value("${ltz.community.auth.jwks-uri}") String jwksUri,
			@Value("${ltz.community.auth.issuer}") String issuer,
			@Value("${ltz.community.auth.service-audience}") String serviceAudience) {
		return decoder(jwksUri, serviceTokenValidator(issuer, serviceAudience, Clock.systemUTC()));
	}

	private static JwtDecoder decoder(String jwksUri, OAuth2TokenValidator<Jwt> validator) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				// typ, doğrulayıcı içinde açıkça denetlenir (at+jwt / svc+jwt).
				.validateType(false)
				// iat/exp/sub bulunmayan token imza doğrulamasından hemen sonra, Spring bu alanları kendiliğinden
				// türetmeden (iat yoksa exp-1 sn atar) ham haliyle reddedilir.
				.jwtProcessorCustomizer(processor -> processor.setJWTClaimsSetVerifier(
						new DefaultJWTClaimsVerifier<>(null, REQUIRED_CLAIMS)))
				.build();
		decoder.setJwtValidator(validator);
		return decoder;
	}

	/** İmza dışındaki tüm kullanıcı token kuralları; JWKS'siz de test edilebilsin diye ayrıdır. */
	public static OAuth2TokenValidator<Jwt> accessTokenValidator(String issuer, String audience, Clock clock) {
		return new DelegatingOAuth2TokenValidator<>(commonRules(issuer, audience, clock, TokenClaims.ACCESS_TOKEN_TYPE));
	}

	/**
	 * Servis token'ı kuralları (docs-auth/servis-sozlesmeleri.md §3): {@code typ=svc+jwt}, {@code iss=ltz-auth},
	 * {@code aud} bu servisin adı, {@code sub=svc:<çağıran>}, {@code jti} zorunlu ve ömür en fazla 60 sn. Kapsam
	 * ({@code scope}) ve çağıranın kimliği ({@code sub}) yetkilendirme kuralında/denetleyicide ayrıca doğrulanır.
	 */
	public static OAuth2TokenValidator<Jwt> serviceTokenValidator(String issuer, String serviceAudience, Clock clock) {
		List<OAuth2TokenValidator<Jwt>> rules = new ArrayList<>(
				commonRules(issuer, serviceAudience, clock, TokenClaims.SERVICE_TOKEN_TYPE));
		rules.add(new JwtClaimValidator<String>(JwtClaimNames.SUB,
				subject -> subject != null && subject.startsWith(TokenClaims.SERVICE_SUBJECT_PREFIX)));
		rules.add(new JwtClaimValidator<Object>(JwtClaimNames.JTI, Objects::nonNull));
		rules.add(maxLifetime(TokenClaims.SERVICE_TOKEN_MAX_LIFETIME, clock));
		return new DelegatingOAuth2TokenValidator<>(rules);
	}

	private static List<OAuth2TokenValidator<Jwt>> commonRules(String issuer, String audience, Clock clock,
			String tokenType) {
		JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
		timestamps.setClock(clock);
		return List.of(
				timestamps,
				new JwtIssuerValidator(issuer),
				new JwtTypeValidator(tokenType),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
						tokenAudience -> tokenAudience != null && tokenAudience.contains(audience)),
				new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
				// Not: Spring iat yoksa exp-1 atadığından bu kural tek başına iat'ın varlığını kanıtlamaz; ömür kısıtı
				// gereken yerde (servis token'ı) bitiş zamanı ayrıca denetlenir.
				new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull),
				new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull));
	}

	/**
	 * Token'ın ömrü {@code limit}'i aşamaz. Spring {@code iat} yoksa kendiliğinden {@code exp - 1 sn} atadığı için yalnız
	 * {@code exp - iat} yetmez; bu yüzden ayrıca bitiş zamanı şimdiden en fazla {@code limit} (+ saat sapması) ileride
	 * olmalıdır. İkisi birlikte, {@code iat} çıkarılarak ömür kuralını aşmayı engeller.
	 */
	private static OAuth2TokenValidator<Jwt> maxLifetime(Duration limit, Clock clock) {
		OAuth2TokenValidatorResult failure = OAuth2TokenValidatorResult.failure(
				new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Token lifetime is too long", null));
		return jwt -> {
			if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
				return failure;
			}
			boolean lifetimeOk = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(limit) <= 0;
			boolean expiresSoon = !jwt.getExpiresAt().isAfter(clock.instant().plus(limit).plus(CLOCK_SKEW));
			return lifetimeOk && expiresSoon ? OAuth2TokenValidatorResult.success() : failure;
		};
	}
}
