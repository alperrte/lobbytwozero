package com.ltz.community.application.port;

import java.util.UUID;

public interface CurrentUserPort {

	/**
	 * Doğrulanmış kimlikten kullanıcı kimliğini döndürür; kimlik yoksa
	 * {@link com.ltz.community.domain.exception.AuthenticationRequiredException} fırlatır.
	 */
	UUID requireUserId();
}
