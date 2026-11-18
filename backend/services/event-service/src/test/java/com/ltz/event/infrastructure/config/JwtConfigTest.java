package com.ltz.event.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtConfigTest {

	private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
	private static final OAuth2TokenValidator<Jwt> VALIDATOR = JwtConfig.accessTokenValidator("ltz-auth", "ltz-api",
			Clock.fixed(NOW, ZoneOffset.UTC));

	private static Jwt jwt(UnaryOperator<Jwt.Builder> change) {
		Jwt.Builder builder = Jwt.withTokenValue("t")
				.header("alg", "RS256")
				.header("typ", "at+jwt")
				.issuer("ltz-auth")
				.audience(List.of("ltz-api"))
				.subject("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66")
				.issuedAt(NOW.minusSeconds(10))
				.notBefore(NOW.minusSeconds(10))
				.expiresAt(NOW.plusSeconds(300));
		return change.apply(builder).build();
	}

	private static boolean accepts(UnaryOperator<Jwt.Builder> change) {
		return !VALIDATOR.validate(jwt(change)).hasErrors();
	}

	@Test
	void validAccessTokenIsAccepted() {
		assertThat(accepts(builder -> builder)).isTrue();
	}

	@Test
	void wrongIssuerIsRejected() {
		assertThat(accepts(builder -> builder.issuer("baska-yayinci"))).isFalse();
	}

	@Test
	void missingAudienceIsRejected() {
		assertThat(accepts(builder -> builder.audience(List.of("baska-servis")))).isFalse();
	}

	@Test
	void serviceTokenTypeIsRejectedOnUserEndpoints() {
		assertThat(accepts(builder -> builder.header("typ", "svc+jwt"))).isFalse();
	}

	@Test
	void expiredTokenIsRejected() {
		assertThat(accepts(builder -> builder.issuedAt(NOW.minusSeconds(900)).notBefore(NOW.minusSeconds(900))
				.expiresAt(NOW.minusSeconds(300)))).isFalse();
	}

	@Test
	void tokenNotYetValidIsRejected() {
		assertThat(accepts(builder -> builder.notBefore(NOW.plusSeconds(600)).expiresAt(NOW.plusSeconds(900)))).isFalse();
	}

	@Test
	void missingSubjectIsRejected() {
		Jwt noSubject = Jwt.withTokenValue("t")
				.header("alg", "RS256")
				.header("typ", "at+jwt")
				.issuer("ltz-auth")
				.audience(List.of("ltz-api"))
				.issuedAt(NOW.minusSeconds(10))
				.notBefore(NOW.minusSeconds(10))
				.expiresAt(NOW.plusSeconds(300))
				.claim("roles", List.of("ADMIN"))
				.build();

		assertThat(VALIDATOR.validate(noSubject).hasErrors()).isTrue();
	}
}
