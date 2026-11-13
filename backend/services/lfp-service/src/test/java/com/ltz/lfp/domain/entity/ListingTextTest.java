package com.ltz.lfp.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;

/** UGC metin kuralları (SECURITY.md §5): boşluk, uzunluk (karakter), kontrol ve görünmez karakterler. */
class ListingTextTest {

    @Test
    void aciklamaKirpilirWindowsSatirSonuCevrilir() {
        assertThat(ListingText.description("  merhaba\r\ndünya\t!  ")).isEqualTo("merhaba\ndünya\t!");
    }

    @Test
    void aciklamaUzunluguKarakterOlarakSayilirEmojiIkiBirimDegil() {
        String emojis = "😀".repeat(1000);
        assertThat(ListingText.description(emojis)).isEqualTo(emojis);
        assertCode(() -> ListingText.description("😀".repeat(1001)), LfpErrorCode.INVALID_DESCRIPTION);
    }

    @Test
    void aciklamaBosNullVeKontrolKarakteriReddedilir() {
        for (String invalid : Arrays.asList(null, "", "  ", "a\u0000b", "a\u001Bb", "yön\u202Edeğişimi", "a\u200Bb")) {
            assertCode(() -> ListingText.description(invalid), LfpErrorCode.INVALID_DESCRIPTION);
        }
    }

    @Test
    void emojiBirlestiricisineIzinVerilir() {
        assertThat(ListingText.description("👨\u200D👩\u200D👧")).isEqualTo("👨\u200D👩\u200D👧");
    }

    @Test
    void seviyeBosIseNullTekSatir50Karakter() {
        assertThat(ListingText.skillLevel(null)).isNull();
        assertThat(ListingText.skillLevel("   ")).isNull();
        assertThat(ListingText.skillLevel(" Elmas 2 ")).isEqualTo("Elmas 2");
        assertThat(ListingText.skillLevel("x".repeat(50))).hasSize(50);
        assertCode(() -> ListingText.skillLevel("x".repeat(51)), LfpErrorCode.INVALID_SKILL_LEVEL);
        assertCode(() -> ListingText.skillLevel("a\nb"), LfpErrorCode.INVALID_SKILL_LEVEL);
        assertCode(() -> ListingText.skillLevel("a\tb"), LfpErrorCode.INVALID_SKILL_LEVEL);
    }

    @Test
    void rollerTekillesirSiraVeIlkYazimKorunur() {
        assertThat(ListingText.roles(List.of(" Tank", "HEALER", "tank ", "healer", "DPS")))
                .containsExactly("Tank", "HEALER", "DPS");
    }

    @Test
    void rolKurallari() {
        assertCode(() -> ListingText.roles(null), LfpErrorCode.INVALID_ROLES);
        assertCode(() -> ListingText.roles(List.of()), LfpErrorCode.INVALID_ROLES);
        assertCode(() -> ListingText.roles(List.of("a", "b", "c", "d", "e", "f")), LfpErrorCode.INVALID_ROLES);
        assertCode(() -> ListingText.roles(Arrays.asList("a", null)), LfpErrorCode.INVALID_ROLES);
        assertCode(() -> ListingText.roles(List.of(" ")), LfpErrorCode.INVALID_ROLES);
        assertCode(() -> ListingText.roles(List.of("x".repeat(31))), LfpErrorCode.INVALID_ROLES);
        assertThat(ListingText.roles(List.of("x".repeat(30)))).hasSize(1);
    }

    @Test
    void rolAnahtariBuyukKucukHarfeDuyarsiz() {
        assertThat(ListingText.roleKey("  Support ")).isEqualTo("support");
    }

    @Test
    void oyunAdiTemizlenirKontrolKarakteriBosluk500KaraktereKisalirBosAdNull() {
        assertThat(ListingText.gameTitle(null)).isNull();
        assertThat(ListingText.gameTitle(" \u0000 ")).isNull();
        assertThat(ListingText.gameTitle(" Half\nLife ")).isEqualTo("Half Life");
        assertThat(ListingText.gameTitle("é".repeat(600))).hasSize(500);
        String emojiTitle = ListingText.gameTitle("😀".repeat(600));
        assertThat(emojiTitle.codePointCount(0, emojiTitle.length())).isEqualTo(500);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, LfpErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(LfpException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
