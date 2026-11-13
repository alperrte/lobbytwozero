package com.ltz.lfp.application.port;

import java.util.UUID;

/**
 * game-service'in ortak oyun kartından LFP'nin ihtiyaç duyduğu alanlar.
 *
 * @param title    temizlenmiş görüntüleme adı (boş değil, en çok 500 karakter)
 * @param gameType game-service'in tür değeri (ör. {@code MAIN_GAME}, {@code DLC}, {@code UNKNOWN})
 */
public record CatalogGame(UUID gameId, String title, String gameType) {
}
