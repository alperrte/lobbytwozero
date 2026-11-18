package com.ltz.event.infrastructure.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ltz.event.application.port.CurrentUserPort;
import com.ltz.event.domain.exception.AuthenticationRequiredException;

/**
 * Kullanıcı kimliği yalnız doğrulanmış JWT'nin {@code sub} claim'inden (auth-service hesap kimliği), rol yalnız
 * {@code roles} claim'inden alınır; istemcinin gönderdiği kimlik/rol başlıkları hiçbir zaman kullanılmaz.
 */
@Component
public class SecurityContextCurrentUser implements CurrentUserPort {

	@Override
	public UUID requireUserId() {
		JwtAuthenticationToken jwtAuthentication = authenticatedJwt();
		if (jwtAuthentication == null) {
			throw new AuthenticationRequiredException("Authentication is required.");
		}
		try {
			return UUID.fromString(jwtAuthentication.getToken().getSubject());
		} catch (IllegalArgumentException | NullPointerException _) {
			throw new AuthenticationRequiredException("Authentication is required.");
		}
	}

	@Override
	public boolean canCreateEvents() {
		JwtAuthenticationToken jwtAuthentication = authenticatedJwt();
		if (jwtAuthentication == null) {
			return false;
		}
		List<String> roles = jwtAuthentication.getToken().getClaimAsStringList(TokenClaims.ROLES);
		return roles != null && roles.stream().anyMatch(EventRoles.EVENT_CREATORS::contains);
	}

	private static JwtAuthenticationToken authenticatedJwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuthentication && authentication.isAuthenticated()) {
			return jwtAuthentication;
		}
		return null;
	}
}
