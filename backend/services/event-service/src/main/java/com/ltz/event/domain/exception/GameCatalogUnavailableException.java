package com.ltz.event.domain.exception;

/**
 * Game Service doğrulanamadı veya aranamadı (zaman aşımı, bağlantı hatası, hata durumu ya da tutarsız/geçersiz yanıt;
 * 503). "Oyun bulunamadı" ile karıştırılmaz: oyun varlığı ve arama sonucu belirsizken etkinlik yazılmaz, eksik sonuç
 * döndürülmez.
 */
public class GameCatalogUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public GameCatalogUnavailableException(Throwable cause) {
		super("The game catalog is temporarily unavailable.", cause);
	}
}
