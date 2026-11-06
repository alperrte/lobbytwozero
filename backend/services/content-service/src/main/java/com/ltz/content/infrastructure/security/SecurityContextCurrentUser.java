package com.ltz.content.infrastructure.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.exception.AuthenticationRequiredException;

/**
 * Kullanıcı kimliği yalnız doğrulanmış JWT'nin {@code sub} claim'inden (auth-service accountId), yönetici rolü yalnız
 * {@code roles} claim'inden alınır; istemcinin gönderdiği kimlik/rol başlıkları ya da istek alanları hiçbir zaman kullanılmaz.
 */
@Component
public class SecurityContextCurrentUser implements CurrentUserPort {

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
	public boolean hasAdminRole() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
			return false;
		}
		List<String> roles = jwtAuthentication.getToken().getClaimAsStringList(TokenClaims.ROLES);
		return roles != null && roles.contains(TokenClaims.ADMIN_ROLE);
	}
}
