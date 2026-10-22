package com.ltz.community.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.ltz.community.domain.exception.AuthenticationRequiredException;

class SecurityContextCurrentUserTest {

	private final SecurityContextCurrentUser currentUser = new SecurityContextCurrentUser();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static JwtAuthenticationToken tokenFor(String subject) {
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject)
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
		return new JwtAuthenticationToken(jwt, List.of());
	}

	@Test
	void findUserIdIsEmptyWithoutAuthentication() {
		assertThat(currentUser.findUserId()).isEmpty();
	}

	@Test
	void findUserIdIsEmptyForAnonymousAuthentication() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		assertThat(currentUser.findUserId()).isEmpty();
		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void findUserIdReturnsSubjectOfVerifiedJwt() {
		UUID id = UUID.randomUUID();
		SecurityContextHolder.getContext().setAuthentication(tokenFor(id.toString()));

		assertThat(currentUser.findUserId()).contains(id);
		assertThat(currentUser.requireUserId()).isEqualTo(id);
	}

	@Test
	void jwtWithNonUuidSubjectIsRejected() {
		SecurityContextHolder.getContext().setAuthentication(tokenFor("not-a-uuid"));

		assertThatThrownBy(currentUser::findUserId).isInstanceOf(AuthenticationRequiredException.class);
	}
}
