package com.ltz.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;

/** Profil metni ve liste alanlarının doğrulama kuralları (veritabanı gerektirmez). */
class ProfileTextTest {

    // ------------------------------------------------------------ görünen ad

    @Test
    void gorunenAdKirpilirBosluklarBirlestirilirUnicodeNfcOlur() {
        assertThat(ProfileText.displayName("  Gölge   Avcı  ")).isEqualTo("Gölge Avcı");
        // "e" + birleşik aksan → tek karakterli "é"
        assertThat(ProfileText.displayName("Café")).isEqualTo("Café");
    }

    @Test
    void gorunenAdBosVeyaYalnizBosluksaTemizlemeSayilir() {
        assertThat(ProfileText.displayName(null)).isNull();
        assertThat(ProfileText.displayName("")).isNull();
        assertThat(ProfileText.displayName("   ")).isNull();
    }

    @Test
    void gorunenAdUzunlukKodNoktasiIleSayilir() {
        assertThat(ProfileText.displayName("ab")).isEqualTo("ab");
        assertThat(ProfileText.displayName("a".repeat(32))).hasSize(32);
        // 32 emoji = 32 kod noktası (64 char) → kabul
        assertThat(ProfileText.displayName("😀".repeat(32))).isNotNull();
        assertThatCode("a", UserErrorCode.INVALID_DISPLAY_NAME, () -> ProfileText.displayName("a"));
        assertThatCode("33", UserErrorCode.INVALID_DISPLAY_NAME, () -> ProfileText.displayName("a".repeat(33)));
        assertThatCode("33 emoji", UserErrorCode.INVALID_DISPLAY_NAME, () -> ProfileText.displayName("😀".repeat(33)));
    }

    @Test
    void gorunenAdGizliVeYonDegistirenKarakterlerReddedilir() {
        String[] forbidden = {"Ad\u202Esoyad", "Ad\u200Bsoyad", "Ad\u0000soyad", "Ad\tsoyad", "Ad\nsoyad",
                "Ad soyad", "Adsoyad", "Ad soyad", "Ad﻿soyad"};
        for (String value : forbidden) {
            assertThatCode(value, UserErrorCode.INVALID_DISPLAY_NAME, () -> ProfileText.displayName(value));
        }
    }

    // ------------------------------------------------------------ biyografi

    @Test
    void biyografiSatirSonlariNormalizeEdilirAmaIzinlidir() {
        assertThat(ProfileText.bio("bir\r\niki\riki")).isEqualTo("bir\niki\niki");
        assertThat(ProfileText.bio("  \n  ")).isNull();
    }

    @Test
    void biyografiSinirVeYasakKarakter() {
        assertThat(ProfileText.bio("b".repeat(500))).hasSize(500);
        assertThatCode("501", UserErrorCode.INVALID_BIO, () -> ProfileText.bio("b".repeat(501)));
        assertThatCode("bidi", UserErrorCode.INVALID_BIO, () -> ProfileText.bio("merhaba\u202Edünya"));
        assertThatCode("tab", UserErrorCode.INVALID_BIO, () -> ProfileText.bio("a\tb"));
    }

    @Test
    void biyografiHtmlDuzMetinOlarakKalir() {
        assertThat(ProfileText.bio("<script>alert(1)</script>")).isEqualTo("<script>alert(1)</script>");
    }

    // ------------------------------------------------------------ ülke

    @Test
    void ulkeIsoKoduBuyukHarfeCevrilir() {
        assertThat(ProfileText.country("tr")).isEqualTo("TR");
        assertThat(ProfileText.country(" De ")).isEqualTo("DE");
        assertThat(ProfileText.country("")).isNull();
        assertThat(ProfileText.country(null)).isNull();
    }

    @Test
    void ulkeGecersizKodReddedilir() {
        for (String value : List.of("ZZ", "TUR", "T", "1A", "T R")) {
            assertThatCode(value, UserErrorCode.INVALID_COUNTRY, () -> ProfileText.country(value));
        }
    }

    // ------------------------------------------------------------ oyuncu türleri

    @Test
    void oyuncuTurleriBuyukHarfeCevrilirYinelenenlerBirlesir() {
        assertThat(ProfileText.playerTypes(List.of("casual", "CASUAL", " story "))).containsExactly("CASUAL", "STORY");
        assertThat(ProfileText.playerTypes(List.of())).isEmpty();
    }

    @Test
    void oyuncuTurleriIkidenFazlaVeBilinmeyenReddedilir() {
        assertThatCode("3", UserErrorCode.INVALID_PLAYER_TYPES,
                () -> ProfileText.playerTypes(List.of("CASUAL", "STORY", "SOCIAL")));
        assertThatCode("bilinmeyen", UserErrorCode.INVALID_PLAYER_TYPES,
                () -> ProfileText.playerTypes(List.of("WIZARD")));
        assertThatCode("null", UserErrorCode.INVALID_PLAYER_TYPES,
                () -> ProfileText.playerTypes(java.util.Arrays.asList("CASUAL", null)));
    }

    // ------------------------------------------------------------ tür ve platform kodları

    @Test
    void kodlarKucukHarfeCevrilirBenzersizYapilir() {
        assertThat(ProfileText.favoriteGenres(List.of("RPG", "rpg", " Action-Adventure ", "co_op")))
                .containsExactly("rpg", "action-adventure", "co_op");
        assertThat(ProfileText.favoritePlatforms(List.of("PC", "ps5"))).containsExactly("pc", "ps5");
    }

    @Test
    void kodlarGecersizBicimVeSinirReddedilir() {
        for (String value : List.of("", "-rpg", "rpg!", "r p g", "rp\ng", "a".repeat(49), "tür")) {
            assertThatCode(value, UserErrorCode.INVALID_FAVORITE_GENRES, () -> ProfileText.favoriteGenres(List.of(value)));
        }
        List<String> eleven = IntStream.range(0, 11).mapToObj(i -> "g" + i).toList();
        assertThatCode("11", UserErrorCode.INVALID_FAVORITE_GENRES, () -> ProfileText.favoriteGenres(eleven));
        assertThat(ProfileText.favoriteGenres(eleven.subList(0, 10))).hasSize(10);
        assertThatCode("platform", UserErrorCode.INVALID_FAVORITE_PLATFORMS,
                () -> ProfileText.favoritePlatforms(List.of("PC!")));
    }

    private static void assertThatCode(String label, UserErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run).as(label)
                .isInstanceOfSatisfying(UserException.class, exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
