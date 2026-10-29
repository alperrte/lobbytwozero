package com.ltz.game.application.service;

import com.ltz.game.domain.enums.GameType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameTypeClassifierTest {
    final GameTypeClassifier classifier=new GameTypeClassifier();
    @ParameterizedTest @CsvSource({"game,MAIN_GAME","main_game,MAIN_GAME","'  MAIN_GAME  ',MAIN_GAME","dlc,DLC","demo,DEMO","music,SOUNDTRACK","soundtrack,SOUNDTRACK",
            "software,SOFTWARE","video,VIDEO","movie,VIDEO","series,VIDEO","package,PACKAGE","bundle,PACKAGE","hardware,HARDWARE","future-type,UNKNOWN"})
    void onlyExplicitReliableTypesAreClassified(String raw,GameType expected) {
        assertEquals(expected,classifier.classify(raw,true));
        assertEquals(GameType.UNKNOWN,classifier.classify(raw,false));
    }
    @Test void missingSignalsStayUnknown() {
        assertEquals(GameType.UNKNOWN,classifier.classify(null,true));
        assertEquals(GameType.UNKNOWN,classifier.classify("",true));
        assertEquals(GameType.UNKNOWN,classifier.classify("Game title Deluxe Demo",true));
    }
}
