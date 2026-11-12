package com.ltz.game.application.service;

import com.ltz.game.domain.enums.GameType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameTypeClassifierTest {
    final GameTypeClassifier classifier=new GameTypeClassifier();
    @ParameterizedTest @CsvSource({"game,MAIN_GAME","main_game,MAIN_GAME","'  MAIN_GAME  ',MAIN_GAME","dlc,DLC","demo,DEMO","music,SOUNDTRACK","soundtrack,SOUNDTRACK",
            "software,SOFTWARE","video,VIDEO","movie,VIDEO","series,VIDEO","package,PACKAGE","bundle,PACKAGE","pack,PACKAGE","hardware,HARDWARE","future-type,UNKNOWN"})
    void onlyExplicitReliableTypesAreClassified(String raw,GameType expected) {
        assertEquals(expected,classifier.classify(raw,true));
        assertEquals(GameType.UNKNOWN,classifier.classify(raw,false));
    }
    @ParameterizedTest @CsvSource({"0,MAIN_GAME","4,DLC","1,UNKNOWN","2,UNKNOWN","3,UNKNOWN","6,UNKNOWN","14,UNKNOWN","-1,UNKNOWN","999,UNKNOWN"})
    void onlySteamGameAndDlcStoreTypesClassify(int steamType,GameType expected) {
        assertEquals(expected,classifier.fromSteamStoreType(steamType));
    }
    @Test void missingSignalsStayUnknown() {
        assertEquals(GameType.UNKNOWN,classifier.classify(null,true));
        assertEquals(GameType.UNKNOWN,classifier.classify("",true));
        assertEquals(GameType.UNKNOWN,classifier.classify("Game title Deluxe Demo",true));
    }
    @ParameterizedTest @CsvSource({"main_game,MAIN_GAME","dlc,DLC","bundle,PACKAGE"})
    void igdbMappedKeysClassifyAndNamesNeverDo(String key,GameType expected) {
        assertEquals(expected,classifier.classify(key,true));
        // Başlık/ad sinyali olarak gelen ifadeler tür kanıtı değildir.
        assertEquals(GameType.UNKNOWN,classifier.classify("Some Game - Soundtrack DLC Demo",true));
    }
}
