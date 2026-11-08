package com.ltz.content.application.port;

import java.util.UUID;

public interface GameCatalogPort {

	/**
	 * LTZ oyun kataloğunda (game-service) bu {@code gameId} ile yayında bir oyun olup olmadığını döndürür.
	 *
	 * @throws com.ltz.content.domain.exception.GameCatalogUnavailableException katalog yanıt vermezse
	 */
	boolean isAvailable(UUID gameId);
}
