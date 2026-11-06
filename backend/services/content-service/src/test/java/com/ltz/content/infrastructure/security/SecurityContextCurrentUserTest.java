package com.ltz.content.infrastructure.security;

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

import com.ltz.content.domain.exception.AuthenticationRequiredException;

class SecurityContextCurrentUserTest {

	private static final UUID USER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");

	private final SecurityContextCurrentUser currentUser = new SecurityContextCurrentUser();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static void authenticate(String subject, Object roles) {
		Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60));
		if (subject != null) {
			builder.subject(subject);
		}
		if (roles != null) {
			builder.claim("roles", roles);
		}
		// Yalnız claim eksikse en az bir alan gerekir.
		builder.claim("typ", "at+jwt");
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
	}

	@Test
	void userIdComesFromTheTokenSubject() {
		authenticate(USER_ID.toString(), List.of("PLAYER"));

		assertThat(currentUser.requireUserId()).isEqualTo(USER_ID);
	}

	@Test
	void noAuthenticationMeansAuthenticationRequired() {
		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void unauthenticatedJwtIsRejectedEvenWithAnAdminClaim() {
		authenticate(USER_ID.toString(), List.of("ADMIN"));
		SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);

		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
		assertThat(currentUser.hasAdminRole()).isFalse();
	}

	@Test
	void authoritiesCannotGrantAdminWithoutTheRolesClaim() {
		authenticate(USER_ID.toString(), List.of("PLAYER"));
		JwtAuthenticationToken authentication = (JwtAuthenticationToken) SecurityContextHolder.getContext()
				.getAuthentication();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(authentication.getToken(),
				AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

		assertThat(currentUser.requireUserId()).isEqualTo(USER_ID);
		assertThat(currentUser.hasAdminRole()).isFalse();
	}

	@Test
	void nonJwtAuthenticationIsNotAccepted() {
		SecurityContextHolder.getContext().setAuthentication(
				new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
		assertThat(currentUser.hasAdminRole()).isFalse();
	}

	@Test
	void aSubjectThatIsNotAUuidIsRejected() {
		authenticate("kullanici-adi", List.of("ADMIN"));

		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void aMissingSubjectIsRejected() {
		authenticate(null, List.of("ADMIN"));

		assertThatThrownBy(currentUser::requireUserId).isInstanceOf(AuthenticationRequiredException.class);
	}

	@Test
	void adminRoleIsReadFromTheRolesClaimOnly() {
		authenticate(USER_ID.toString(), List.of("PLAYER", "ADMIN"));

		assertThat(currentUser.hasAdminRole()).isTrue();
	}

	@Test
	void otherRolesMissingRolesAndLookalikeRolesAreNotAdmin() {
		authenticate(USER_ID.toString(), List.of("PLAYER", "MODERATOR", "CONTENT_CREATOR", "admin", "ROLE_ADMIN"));
		assertThat(currentUser.hasAdminRole()).isFalse();

		authenticate(USER_ID.toString(), null);
		assertThat(currentUser.hasAdminRole()).isFalse();
	}

	@Test
	void noAuthenticationIsNeverAdmin() {
		assertThat(currentUser.hasAdminRole()).isFalse();
	}
}
