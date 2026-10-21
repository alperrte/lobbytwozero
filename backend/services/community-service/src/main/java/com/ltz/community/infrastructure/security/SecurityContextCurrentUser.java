package com.ltz.community.infrastructure.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.exception.AuthenticationRequiredException;

/** Kullanıcı kimliği yalnız doğrulanmış JWT'nin {@code sub} claim'inden (auth-service accountId varsayımı) alınır. */
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
}
