package com.ltz.content.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.ltz.content.AuthTestSupport;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Kullanıcı token'ı doğrulayıcısını gerçek imza ve JWKS ile sınar: Auth'un sözleşmesindeki her kural için hem kabul hem
 * ret durumu vardır (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3.2).
 */
class JwtConfigTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");

	private final JwtDecoder users = new JwtConfig().userTokenDecoder(AuthTestSupport.jwksUri(), AuthTestSupport.ISSUER,
			AuthTestSupport.USER_AUDIENCE);

	private final JwtDecoder services = new JwtConfig().serviceTokenDecoder(AuthTestSupport.jwksUri(), AuthTestSupport.ISSUER,
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

	@Test
	void validUserTokenIsAcceptedAndExposesSubjectAndRoles() {
		Jwt jwt = users.decode(AuthTestSupport.userToken(USER_ID, "PLAYER", "ADMIN"));

		assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("PLAYER", "ADMIN");
	}

	@Test
	void wrongIssuerAudienceOrTokenTypeIsRejected() {
		assertRejected(users, user(claims -> claims.issuer("baska-yayinci")));
		assertRejected(users, user(claims -> claims.audience("baska-api")));
		// Servis token'ı (svc+jwt) bu serviste kabul edilmez.
		assertRejected(users, AuthTestSupport.serviceToken("svc:auth-service", "events:ingest"));
	}

	@Test
	void expiredNotYetValidOrIncompleteTokensAreRejected() {
		Instant now = Instant.now();
		assertRejected(users, user(claims -> claims.expirationTime(at(now.minusSeconds(600)))));
		assertRejected(users, user(claims -> claims.notBeforeTime(at(now.plusSeconds(600)))));
		assertRejected(users, user(claims -> claims.subject(null)));
		assertRejected(users, user(claims -> claims.expirationTime(null)));
		assertRejected(users, user(claims -> claims.notBeforeTime(null)));
		assertRejected(users, user(claims -> claims.issueTime(null)));
	}

	@Test
	void nonRs256OrUnsignedOrUnknownKeyTokensAreRejected() {
		JWTClaimsSet valid = AuthTestSupport.validUserClaims(USER_ID).build();
		RSAKey stranger = AuthTestSupport.generateKey("yabanci-anahtar");

		assertRejected(users, AuthTestSupport.hs256Token(valid, AuthTestSupport.accessType()));
		assertRejected(users, AuthTestSupport.unsignedToken(valid));
		// JWKS'te bulunmayan anahtarla (bilinmeyen kid) imzalanmış token.
		assertRejected(users, AuthTestSupport.sign(stranger, AuthTestSupport.accessType(), valid));
		assertRejected(users, "bu-bir-jwt-degil");
	}

	@Test
	void validatorCanBeUsedWithoutJwks() {
		Jwt token = Jwt.withTokenValue("t").header("alg", "RS256").header("typ", "at+jwt").issuer("ltz-auth")
				.audience(java.util.List.of("ltz-api")).subject("s").issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60)).build();

		assertThat(JwtConfig.accessTokenValidator("ltz-auth", "ltz-api", java.time.Clock.systemUTC()).validate(token)
				.hasErrors()).isFalse();
		assertThat(JwtConfig.accessTokenValidator("ltz-auth", "baska-api", java.time.Clock.systemUTC()).validate(token)
				.hasErrors()).isTrue();
	}
	// ------------------------------------------------------------------ servis token'ı (svc+jwt), yalnız /internal/** için

	@Test
	void validServiceTokenIsAcceptedAndExposesSubjectAndScope() {
		Jwt jwt = services.decode(AuthTestSupport.serviceToken("svc:admin-service", "events:ingest"));

		assertThat(jwt.getSubject()).isEqualTo("svc:admin-service");
		assertThat(jwt.<String>getClaim("scope")).isEqualTo("events:ingest");
	}

	@Test
	void userTokensAreNotServiceTokensAndServiceTokensAreNotUserTokens() {
		assertRejected(services, AuthTestSupport.userToken(USER_ID, "ADMIN"));
		assertRejected(users, AuthTestSupport.serviceToken("svc:admin-service", "events:ingest"));
	}

	@Test
	void serviceTokensWithWrongSubjectOrAudienceAreRejected() {
		assertRejected(services, AuthTestSupport.serviceToken(USER_ID.toString(), "events:ingest"));
		assertRejected(services, AuthTestSupport.serviceToken("admin-service", "events:ingest"));
		JwtDecoder otherAudience = new JwtConfig().serviceTokenDecoder(AuthTestSupport.jwksUri(), AuthTestSupport.ISSUER,
				"community-service");
		assertRejected(otherAudience, AuthTestSupport.serviceToken("svc:admin-service", "events:ingest"));
	}
}
