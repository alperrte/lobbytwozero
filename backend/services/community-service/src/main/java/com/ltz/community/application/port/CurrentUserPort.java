package com.ltz.community.application.port;

import java.util.Optional;
import java.util.UUID;

public interface CurrentUserPort {

	/**
	 * Doğrulanmış kimlikten kullanıcı kimliğini döndürür; kimlik yoksa
	 * {@link com.ltz.community.domain.exception.AuthenticationRequiredException} fırlatır.
	 */
	UUID requireUserId();

	/** Misafir (kimliksiz) okumalar için: doğrulanmış kimlik varsa kullanıcı kimliği, yoksa boş. */
	Optional<UUID> findUserId();

	/**
	 * Doğrulanmış kimlik moderatör ({@code MODERATOR}) ya da yönetici ({@code ADMIN}) rolündeyse true döner; kimlik
	 * yoksa ya da rol yoksa false (fail-closed). Rol, JWT'nin {@code roles} claim'inden okunur (auth-service
	 * sözleşmesi netleşene kadar varsayım).
	 */
	boolean hasModeratorRole();
}
