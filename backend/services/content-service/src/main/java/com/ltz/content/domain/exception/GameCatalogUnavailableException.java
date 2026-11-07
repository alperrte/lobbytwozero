package com.ltz.content.domain.exception;

/** game-service doğrulanamadı (zaman aşımı, bağlantı veya beklenmeyen yanıt); oyun ilişkisi doğrulanmadan yazılmaz. */
public class GameCatalogUnavailableException extends RuntimeException {

	public GameCatalogUnavailableException(Throwable cause) {
		super("The game catalog is temporarily unavailable.", cause);
	}
}
