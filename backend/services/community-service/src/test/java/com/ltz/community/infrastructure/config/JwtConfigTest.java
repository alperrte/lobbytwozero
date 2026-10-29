package com.ltz.community.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.ltz.community.AuthTestSupport;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Kullanıcı ve servis token'ı doğrulayıcılarını gerçek imza ve JWKS ile sınar: Auth'un sözleşmesindeki her kural için
 * hem kabul hem ret durumu vardır (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3.2, §11; docs-auth/servis-sozlesmeleri.md §3).
 */
class JwtConfigTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID ACCOUNT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");

	private final JwtConfig config = new JwtConfig();
	private final JwtDecoder users = config.userTokenDecoder(AuthTestSupport.jwksUri(), AuthTestSupport.ISSUER,
			AuthTestSupport.USER_AUDIENCE);
	private final JwtDecoder services = config.serviceTokenDecoder(AuthTestSupport.jwksUri(), AuthTestSupport.ISSUER,
			AuthTestSupport.SERVICE_AUDIENCE);

	private static void assertRejected(JwtDecoder decoder, String token) {
		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	private static Date at(Instant instant) {
		return Date.from(instant);
	}

	private static String user(UnaryOperator<JWTClaimsSet.Builder> change) {
		return AuthTestSupport.userToken(USER_ID, change);
	}

	private static String service(UnaryOperator<JWTClaimsSet.Builder> change) {
		return AuthTestSupport.serviceToken(AuthTestSupport.AUTH_SUBJECT, AuthTestSupport.ERASE_SCOPE, change);
	}

	// ------------------------------------------------------------------ kullanıcı token'ı

	@Test
	void validUserTokenIsAcceptedAndExposesSubjectAndRoles() {
		Jwt jwt = users.decode(AuthTestSupport.userToken(USER_ID, "PLAYER", "MODERATOR"));

		assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("PLAYER", "MODERATOR");
	}

	@Test
	void userTokenWithWrongIssuerAudienceOrTypeIsRejected() {
		assertRejected(users, user(claims -> claims.issuer("baska-yayinci")));
		assertRejected(users, user(claims -> claims.audience("baska-api")));
		// Servis token'ı (svc+jwt, aud=community-service) kullanıcı doğrulayıcısında geçmez.
		assertRejected(users, AuthTestSupport.eraseToken(ACCOUNT_ID));
	}

	@Test
	void userTokenWithMissingOrExpiredTimesOrSubjectIsRejected() {
		Instant now = Instant.now();
		assertRejected(users, user(claims -> claims.expirationTime(at(now.minusSeconds(600)))));
		assertRejected(users, user(claims -> claims.notBeforeTime(at(now.plusSeconds(600)))));
		assertRejected(users, user(claims -> claims.subject(null)));
		assertRejected(users, user(claims -> claims.expirationTime(null)));
	}

	@Test
	void nonRs256OrUnsignedOrUnknownKeyTokensAreRejected() {
		JWTClaimsSet valid = AuthTestSupport.validUserClaims(USER_ID).build();
		RSAKey stranger = AuthTestSupport.generateKey("yabanci-anahtar");

		assertRejected(users, AuthTestSupport.hs256Token(valid, AuthTestSupport.accessType()));
		assertRejected(users, AuthTestSupport.unsignedToken(valid));
		// JWKS'te bulunmayan anahtarla (bilinmeyen kid) imzalanmış token.
		String forged = AuthTestSupport.sign(stranger, AuthTestSupport.accessType(), valid);
		assertRejected(users, forged);
		assertRejected(users, "bu-bir-jwt-degil");
	}

	@Test
	void keyPublishedAfterStartupIsPickedUpOnNextUnknownKid() {
		RSAKey rotated = AuthTestSupport.generateKey("donusturulmus-anahtar");
		String token = AuthTestSupport.sign(rotated, AuthTestSupport.accessType(),
				AuthTestSupport.validUserClaims(USER_ID).build());
		assertRejected(users, token);

		AuthTestSupport.publishKey(rotated);

		assertThat(users.decode(token).getSubject()).isEqualTo(USER_ID.toString());
	}

	// ------------------------------------------------------------------ servis token'ı

	@Test
	void validServiceTokenIsAcceptedWithAllContractClaims() {
		Jwt jwt = services.decode(AuthTestSupport.eraseToken(ACCOUNT_ID));

		assertThat(jwt.getSubject()).isEqualTo("svc:auth-service");
		assertThat(jwt.getClaimAsString("scope")).isEqualTo("account:erase");
		assertThat(jwt.getClaimAsString("account_id")).isEqualTo(ACCOUNT_ID.toString());
		assertThat(jwt.getAudience()).containsExactly("community-service");
	}

	@Test
	void serviceTokenOfTheWrongKindAudienceIssuerOrCallerIsRejected() {
		// Kullanıcı token'ı (at+jwt, aud=ltz-api) iç uçta geçmez.
		assertRejected(services, AuthTestSupport.userToken(USER_ID, "ADMIN"));
		assertRejected(services, service(claims -> claims.audience("social-service")));
		assertRejected(services, service(claims -> claims.audience("ltz-api")));
		assertRejected(services, service(claims -> claims.issuer("baska-yayinci")));
		assertRejected(services, service(claims -> claims.subject(USER_ID.toString())));
	}

	@Test
	void serviceTokenLifetimeJtiAndIssuedAtRulesAreEnforced() {
		Instant now = Instant.now();
		assertRejected(services, service(claims -> claims.expirationTime(at(now.plusSeconds(120)))));
		assertRejected(services, service(claims -> claims.issueTime(at(now.minusSeconds(120))) .expirationTime(at(now.minusSeconds(60)))));
		assertRejected(services, service(claims -> claims.jwtID(null)));
		// iat yoksa Spring exp-1 atar; bitiş zamanı ayrıca sınırlandığından uzun ömürlü token yine reddedilir.
		assertRejected(services, service(claims -> claims.issueTime(null).expirationTime(at(now.plusSeconds(3600)))));
	}

	@Test
	void serviceTokenWithNonRs256AlgorithmsIsRejected() {
		JWTClaimsSet valid = AuthTestSupport.validServiceClaims(AuthTestSupport.AUTH_SUBJECT, AuthTestSupport.ERASE_SCOPE).build();

		assertRejected(services, AuthTestSupport.hs256Token(valid, AuthTestSupport.serviceType()));
		assertRejected(services, AuthTestSupport.unsignedToken(valid));
	}

	@Test
	void validatorsCanBeUsedWithoutJwksAndShareTheCommonRules() {
		Jwt notYetValid = Jwt.withTokenValue("t").header("alg", "RS256").header("typ", "at+jwt").issuer("ltz-auth")
				.audience(List.of("ltz-api")).subject("s").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

		assertThat(JwtConfig.accessTokenValidator("ltz-auth", "ltz-api", java.time.Clock.systemUTC()).validate(notYetValid)
				.hasErrors()).isFalse();
		assertThat(JwtConfig.serviceTokenValidator("ltz-auth", "community-service", java.time.Clock.systemUTC())
				.validate(notYetValid).hasErrors()).isTrue();
	}
}
