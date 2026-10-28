package com.ltz.community.infrastructure.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.exception.AuthenticationRequiredException;

/**
 * Kullanıcı kimliği yalnız doğrulanmış JWT'nin {@code sub} claim'inden (auth-service accountId varsayımı), moderatör
 * rolü yalnız {@code roles} claim'inden alınır; istemcinin gönderdiği kimlik/rol başlıkları hiçbir zaman kullanılmaz.
 */
@Component
public class SecurityContextCurrentUser implements CurrentUserPort {

	private static final String ROLES_CLAIM = "roles";
	private static final Set<String> MODERATOR_ROLES = Set.of("MODERATOR", "ADMIN");

	@Override
	public UUID requireUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
			throw new AuthenticationRequiredException("Authentication is required.");
		}
		try {
			return UUID.fromString(jwtAuthentication.getToken().getSubject());
		} catch (IllegalArgumentException | NullPointerException _) {
			throw new AuthenticationRequiredException("Authentication is required.");
		}
	}

	@Override
	public Optional<UUID> findUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken) || !authentication.isAuthenticated()) {
			return Optional.empty();
		}
		return Optional.of(requireUserId());
	}

	@Override
	public boolean hasModeratorRole() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
			return false;
		}
		List<String> roles = jwtAuthentication.getToken().getClaimAsStringList(ROLES_CLAIM);
		return roles != null && roles.stream().anyMatch(MODERATOR_ROLES::contains);
	}
}
