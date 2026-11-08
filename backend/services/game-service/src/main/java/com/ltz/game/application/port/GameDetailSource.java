package com.ltz.game.application.port;

/**
 * Katalog sağlayıcısından ayrı, yalnız detay zenginleştiren dış kaynak (ör. IGDB). Katalog (Steam AppID) ile
 * detay kaynağı birbirinin yerine geçmez; kaynak, Steam AppID'sinden tam dış kimlik kanıtıyla eşlenir.
 */
public interface GameDetailSource {

    /** Provenance ve atıf için kaynak adı (ör. {@code IGDB}); büyük harf, ≤32 karakter. */
    String source();

    /** Kaynak, oyunun türünü ({@link GameProvider.Details#providerType()}) güvenilir şekilde bildiriyorsa true. */
    default boolean reportsItemType() { return false; }

    /**
     * Steam AppID listesi için toplu kanıt (AppID → tür ve çıkış tarihi). Yalnız açık kanıt olan AppID'ler döner.
     * @throws com.ltz.game.domain.exception.ProviderException erişim/yanıt hatası veya desteklenmiyorsa
     */
    default java.util.Map<String, GameProvider.ItemEvidence> itemEvidence(java.util.Collection<String> steamAppIds) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }

    /** Toplu detay (en çok 100 AppID); bkz. {@link GameProvider#bulkDetails}. */
    default java.util.Map<String, GameProvider.Details> bulkDetails(java.util.Collection<String> steamAppIds) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }

    /** Toplu ilişki kanıtı (tür kimliği ve üst oyun); bkz. {@link GameProvider#relationEvidence}. */
    default java.util.Map<String, GameProvider.RelationEvidence> relationEvidence(java.util.Collection<String> steamAppIds) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }

    /**
     * @param steamAppId katalogdaki Steam AppID (yalnız rakam)
     * @return yalnız kaynağın gerçekten sunduğu alanlar
     * @throws com.ltz.game.domain.exception.ProviderException eşleme bulunamadı/belirsiz, erişim veya yanıt hatası
     */
    GameProvider.Details details(String steamAppId);
}
