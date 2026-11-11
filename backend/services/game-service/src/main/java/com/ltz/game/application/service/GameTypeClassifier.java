package com.ltz.game.application.service;

import com.ltz.game.domain.enums.GameType;
import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.Map;

/** Oyun adına bakmaz. Sadece adaptörün gerçekten sunduğu belgeli tür sinyali kullanılır. */
@Component
public class GameTypeClassifier {
    private static final Map<String, GameType> TYPES = Map.ofEntries(
            Map.entry("game", GameType.MAIN_GAME), Map.entry("main_game", GameType.MAIN_GAME),
            Map.entry("dlc", GameType.DLC), Map.entry("demo", GameType.DEMO),
            Map.entry("music", GameType.SOUNDTRACK), Map.entry("soundtrack", GameType.SOUNDTRACK),
            Map.entry("software", GameType.SOFTWARE), Map.entry("movie", GameType.VIDEO),
            Map.entry("video", GameType.VIDEO), Map.entry("series", GameType.VIDEO),
            Map.entry("package", GameType.PACKAGE), Map.entry("bundle", GameType.PACKAGE), Map.entry("pack", GameType.PACKAGE),
            Map.entry("hardware", GameType.HARDWARE));

    /**
     * Steam mağaza türü (yedek kanıt): 0 oyun, 4 DLC. Demo (1), müzik, yazılım, video ve tanımadığımız her değer UNKNOWN kalır; kayıt listeye
     * girmez. Canlı veriyle kalibre edilmiştir (IGDB'nin sınıfladığı ana oyunların tamamı type=0, DLC'lerin %99,3'ü type=4).
     */
    public GameType fromSteamStoreType(int steamType) {
        return switch (steamType) {
            case 0 -> GameType.MAIN_GAME;
            case 4 -> GameType.DLC;
            default -> GameType.UNKNOWN;
        };
    }

    public GameType classify(String providerType, boolean supportedSignal) {
        if (!supportedSignal || providerType==null) return GameType.UNKNOWN;
        return TYPES.getOrDefault(providerType.strip().toLowerCase(Locale.ROOT), GameType.UNKNOWN);
    }
}
