package com.ltz.event.application.port;

import java.util.UUID;

public interface CurrentUserPort {

	/**
	 * Doğrulanmış kimlikten kullanıcı kimliğini ({@code sub}) döndürür; kimlik yoksa ya da geçersizse
	 * {@link com.ltz.event.domain.exception.AuthenticationRequiredException} fırlatır. İstemcinin gönderdiği hiçbir
	 * kimlik değeri (body, query, header) kullanılmaz.
	 */
	UUID requireUserId();

	/**
	 * Doğrulanmış kimlik etkinlik oluşturmaya yetkili bir role ({@code PLAYER}, {@code MODERATOR}, {@code ADMIN})
	 * sahipse true; kimlik yoksa ya da rol yoksa false (fail-closed). Rol, JWT'nin {@code roles} claim'inden okunur.
	 */
	boolean canCreateEvents();
}
