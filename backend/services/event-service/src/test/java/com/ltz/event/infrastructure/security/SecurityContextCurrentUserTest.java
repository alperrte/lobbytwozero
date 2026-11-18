package com.ltz.event.infrastructure.security;

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

import com.ltz.event.domain.exception.AuthenticationRequiredException;

class SecurityContextCurrentUserTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");

	private final SecurityContextCurrentUser currentUser = new SecurityContextCurrentUser();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static JwtAuthenticationToken tokenFor(String subject, List<String> roles) {
		Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60));
		if (subject != null) {
			builder.subject(subject);
		}
		if (roles != null) {
			builder.claim("roles", roles);
		}
		return new JwtAuthenticationToken(builder.build(), List.of());
	}

	@Test
	void userIdComesFromTheSubjectOfTheVerifiedJwt() {
		SecurityContextHolder.getContext().setAuthentication(tokenFor(USER_ID.toString(), List.of("ADMIN")));

		assertThat(currentUser.requireUserId()).isEqualTo(USER_ID);
	}

	@Test
	void missingAuthenticationIsRejected() {
		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
		assertThat(currentUser.canCreateEvents()).isFalse();
	}

	@Test
	void anonymousAuthenticationIsRejected() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
		assertThat(currentUser.canCreateEvents()).isFalse();
	}

	@Test
	void subjectThatIsNotAUuidIsRejected() {
		SecurityContextHolder.getContext().setAuthentication(tokenFor("svc:auth-service", List.of("ADMIN")));

		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void missingSubjectIsRejected() {
		SecurityContextHolder.getContext().setAuthentication(tokenFor(null, List.of("ADMIN")));

		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void playerModeratorAndAdminCanCreateEvents() {
		for (String role : List.of("PLAYER", "MODERATOR", "ADMIN")) {
			SecurityContextHolder.getContext().setAuthentication(tokenFor(USER_ID.toString(), List.of(role)));

			assertThat(currentUser.canCreateEvents()).as(role).isTrue();
		}
	}

	@Test
	void accountWithAdditionalRolesCanCreateEventsThroughThePlayerBaseRole() {
		SecurityContextHolder.getContext().setAuthentication(
				tokenFor(USER_ID.toString(), List.of("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER")));

		assertThat(currentUser.canCreateEvents()).isTrue();
	}

	@Test
	void unknownOrMalformedRolesCannotCreateEvents() {
		for (String role : List.of("CONTENT_CREATOR", "GAME_DEVELOPER", "player", "admin", "ROLE_ADMIN", "")) {
			SecurityContextHolder.getContext().setAuthentication(tokenFor(USER_ID.toString(), List.of(role)));

			assertThat(currentUser.canCreateEvents()).as(role).isFalse();
		}
	}

	@Test
	void missingRolesClaimCannotCreateEvents() {
		SecurityContextHolder.getContext().setAuthentication(tokenFor(USER_ID.toString(), null));

		assertThat(currentUser.canCreateEvents()).isFalse();
	}
}
