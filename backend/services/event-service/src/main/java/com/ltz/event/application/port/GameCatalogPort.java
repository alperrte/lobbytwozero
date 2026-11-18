package com.ltz.event.application.port;

import java.util.Set;
import java.util.UUID;

/** LTZ oyun kataloğu (Game Service) ile sözleşme. Başka servisin veritabanına erişilmez; yalnız herkese açık uçlar kullanılır. */
public interface GameCatalogPort {

	/**
	 * Bu {@code gameId} ile yayında (ACTIVE) bir oyun var mı (EVT-17). Oyun adı veya başka veri döndürülmez/saklanmaz.
	 *
	 * @throws com.ltz.event.domain.exception.GameCatalogUnavailableException katalog yanıt vermezse veya yanıt geçersizse
	 */
	boolean isAvailable(UUID gameId);

	/**
	 * Adı {@code text} ile başlayan oyunların kimliklerini <b>eksiksiz</b> döndürür (EVT-05). Game Service araması
	 * yerel <b>önek</b> aramasıdır ve yalnız ana oyunları (ACTIVE + MAIN_GAME) kapsar; tüm sayfalar okunur. Sonuç
	 * kümesi eksik olacaksa (üst sınır aşımı, sayfa tutarsızlığı) kırpılmış sonuç döndürülmez.
	 *
	 * @throws com.ltz.event.domain.exception.GameSearchTooBroadException eşleşen oyun sayısı üst sınırı aşarsa
	 * @throws com.ltz.event.domain.exception.GameCatalogUnavailableException katalog yanıt vermezse veya yanıt tutarsızsa
	 */
	Set<UUID> findGameIdsByNamePrefix(String text);
}
