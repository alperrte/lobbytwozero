package com.ltz.game.application.service;

import com.ltz.game.domain.enums.GameType;
import org.springframework.stereotype.Component;
import java.util.Locale;

/** Oyun adına bakmaz. Sadece adaptörün gerçekten sunduğu belgeli tür sinyali kullanılır. */
@Component
public class GameTypeClassifier {
    public GameType classify(String providerType, boolean supportedSignal) {
        if (!supportedSignal || providerType==null) return GameType.UNKNOWN;
        return switch(providerType.strip().toLowerCase(Locale.ROOT)) {
            case "game", "main_game" -> GameType.MAIN_GAME;
            case "dlc" -> GameType.DLC;
            case "demo" -> GameType.DEMO;
            case "music", "soundtrack" -> GameType.SOUNDTRACK;
            case "software" -> GameType.SOFTWARE;
            case "movie", "video", "series" -> GameType.VIDEO;
            case "package", "bundle" -> GameType.PACKAGE;
            case "hardware" -> GameType.HARDWARE;
            default -> GameType.UNKNOWN;
        };
    }
}
