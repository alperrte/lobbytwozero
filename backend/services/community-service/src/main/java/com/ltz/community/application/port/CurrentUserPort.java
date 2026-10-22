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
}
