package com.ltz.community.domain.exception;

/** game-service doğrulanamadı (zaman aşımı, bağlantı veya beklenmeyen yanıt); gönderi gameId'siz yazılmaz. */
public class GameCatalogUnavailableException extends RuntimeException {

	public GameCatalogUnavailableException(Throwable cause) {
		super("The game catalog is temporarily unavailable.", cause);
	}
}
