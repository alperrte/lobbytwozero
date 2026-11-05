package com.ltz.game.application.port;

/**
 * Katalog sağlayıcısından ayrı, yalnız detay zenginleştiren dış kaynak (ör. IGDB). Katalog (Steam AppID) ile
 * detay kaynağı birbirinin yerine geçmez; kaynak, Steam AppID'sinden tam dış kimlik kanıtıyla eşlenir.
 */
public interface GameDetailSource {

    /** Provenance ve atıf için kaynak adı (ör. {@code IGDB}); büyük harf, ≤32 karakter. */
    String source();

    /**
     * @param steamAppId katalogdaki Steam AppID (yalnız rakam)
     * @return yalnız kaynağın gerçekten sunduğu alanlar
     * @throws com.ltz.game.domain.exception.ProviderException eşleme bulunamadı/belirsiz, erişim veya yanıt hatası
     */
    GameProvider.Details details(String steamAppId);
}
