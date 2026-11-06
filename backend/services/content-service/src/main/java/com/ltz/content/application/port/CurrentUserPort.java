package com.ltz.content.application.port;

import java.util.Optional;
import java.util.UUID;

public interface CurrentUserPort {

	/**
	 * Doğrulanmış kimlikten kullanıcı kimliğini döndürür; kimlik yoksa
	 * {@link com.ltz.content.domain.exception.AuthenticationRequiredException} fırlatır. Kimlik yalnız doğrulanmış
	 * token'dan gelir; istek gövdesi, sorgu ya da başlıklardaki kimlik kullanılmaz.
	 */
	UUID requireUserId();

	/** Herkese açık okumalar için: doğrulanmış kimlik varsa kullanıcı kimliği, misafirde boş. */
	Optional<UUID> findUserId();

	/**
	 * Doğrulanmış kimlik yönetici ({@code ADMIN}) rolündeyse true döner; kimlik ya da rol yoksa false (fail-closed). Rol,
	 * JWT'nin {@code roles} claim'inden okunur (auth-service sözleşmesi).
	 */
	boolean hasAdminRole();
}
