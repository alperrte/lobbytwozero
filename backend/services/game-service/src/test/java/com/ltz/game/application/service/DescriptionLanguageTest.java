package com.ltz.game.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Türkçe dil denetimi: İngilizce (veya başka dil) metin yanlışlıkla Türkçe sayılmaz; şüphede reddedilir. */
class DescriptionLanguageTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "Bu, Super Mario'ya benzeyen güzel kızlar için mükemmel bir oyun.",
            "Karanlık bir dünyada hayatta kalmaya çalış.",
            "Kendi şehrini kur ve yönet",
            "Oyuncular ve takımlar için bir macera ile daha fazlası"})
    void turkishTextIsAccepted(String text) { assertTrue(DescriptionLanguage.accepts("tr", text), text); }

    @ParameterizedTest
    @ValueSource(strings = {
            "The most unique and interesting overhead shooter on Steam.",
            "A space game about swinging trash into things and dying.",
            "Embark on an incredible adventure on an island where 100 adorable animals await you!",
            "Entdecke eine wunderschöne Welt voller Überraschungen und Rätsel.",
            "Découvrez un monde plein de surprises, çà et là.",
            "   ", ""})
    void englishAndOtherLanguagesAreRejected(String text) { assertFalse(DescriptionLanguage.accepts("tr", text), text); }

    @ParameterizedTest
    @ValueSource(strings = {
            "A space game about swinging trash into things and dying.",
            "Embark on an incredible adventure on an island where 100 adorable animals await you!",
            "The most unique and interesting overhead shooter on Steam, with a new twist."})
    void englishTextIsAcceptedAsEnglish(String text) { assertTrue(DescriptionLanguage.looksEnglish(text), text); }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bu, Super Mario'ya benzeyen güzel kızlar için mükemmel bir oyun.",
            "ใน Professional Chef เปลี่ยนวัตถุดิบง่าย ๆ ให้กลายเป็นอาหารจานอร่อย",
            "这是一个关于在岛屿上寻找一百只可爱动物的冒险游戏",
            "Entdecke eine wunderschöne Welt voller Überraschungen und Rätsel.",
            "Игра про выживание в мрачном мире",
            "Blah", "   ", ""})
    void otherLanguagesAndTextWithoutEnglishEvidenceAreNotMarkedEnglish(String text) { assertFalse(DescriptionLanguage.looksEnglish(text), text); }

    @Test void nullIsNotEnglish() { assertFalse(DescriptionLanguage.looksEnglish(null)); }

    @Test void nullAndUnsupportedLanguagesAreRejected() {
        assertFalse(DescriptionLanguage.accepts("tr", null));
        assertFalse(DescriptionLanguage.accepts("de", "Bu bir Türkçe metin çok güzel ve değil"), "denetimi tanımlı olmayan dil asla kabul edilmez");
        assertTrue(DescriptionLanguage.supports("tr"));
        assertFalse(DescriptionLanguage.supports("en"));
        assertFalse(DescriptionLanguage.supports("de"));
    }

    @Test void fewTurkishWordsWithoutDistinctiveLettersAreNotEnough() {
        assertFalse(DescriptionLanguage.accepts("tr", "ve bir oyun"), "iki sözcük ve harf kanıtı yok");
    }
}
