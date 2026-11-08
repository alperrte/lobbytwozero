package com.ltz.game.application.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Steam mağaza sayfasının herkese açık verisi (katalog/IGDB'nin vermediği veya eksik bıraktığı alanlar için tamamlayıcı kaynak):
 * başlık görseli, geliştirici, yayıncı, dil desteği ve (DLC ise) ana oyun AppID'si. Katalog kimliği yine Steam AppID'sidir.
 */
public interface StoreDetailSource {

    /** Provenance için kaynak adı. */
    default String source() { return "STEAM"; }

    /**
     * @param appId Steam AppID (yalnız rakam)
     * @return mağazada herkese açık veri yoksa (kaldırılmış, bölge kısıtı) boş
     * @throws com.ltz.game.domain.exception.ProviderException erişim/hız sınırı/yanıt hatası
     */
    Optional<StoreDetails> details(String appId);

    /**
     * @param headerImageUrl normalleştirilmiş kalıcı başlık görseli adresi (yoksa null)
     * @param languages arayüz her listelenen dil için SUPPORTED; ses yalnız Steam'in "tam ses desteği" işaretliyse SUPPORTED,
     *                  aksi halde UNKNOWN; altyazı UNKNOWN (Steam ayırmaz)
     * @param fullGameAppId DLC'nin ana oyun AppID'si; yoksa null
     */
    record StoreDetails(String type, String headerImageUrl, Set<String> developers, Set<String> publishers,
                        List<GameProvider.LanguageSupport> languages, String fullGameAppId) {
        public StoreDetails {
            developers = developers == null ? Set.of() : Set.copyOf(developers);
            publishers = publishers == null ? Set.of() : Set.copyOf(publishers);
            languages = languages == null ? List.of() : List.copyOf(languages);
        }
    }
}
