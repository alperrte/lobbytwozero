package com.ltz.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ltz.user.TestImages;

/** GIF yapı doğrulaması ve temizliği (çözme yapılmaz). */
class GifSanitizerTest {

    @Test
    void yorumBlokuAtilirDongBloguVeKareSayisiKorunur() {
        byte[] gif = TestImages.gif(3, true, 32, 16);

        GifSanitizer.Result result = GifSanitizer.sanitize(gif, 100);

        assertThat(result.frames()).isEqualTo(3);
        assertThat(result.width()).isEqualTo(32);
        assertThat(result.height()).isEqualTo(16);
        String text = new String(result.bytes(), StandardCharsets.ISO_8859_1);
        assertThat(text).contains("NETSCAPE2.0").doesNotContain("hello");
        assertThat(result.bytes()).hasSize(gif.length - 9); // yorum bloğu: 2 + 1 + 5 + 1 bayt
    }

    @Test
    void yorumsuzGifAyniKalir() {
        byte[] gif = TestImages.gif(1, false, 8, 8);

        assertThat(GifSanitizer.sanitize(gif, 100).bytes()).isEqualTo(gif);
    }

    @Test
    void bilinmeyenUygulamaBloguAtilir() {
        byte[] gif = TestImages.gif(1, false, 8, 8);
        // NETSCAPE bloğunun hemen önüne "EVIL1234ABC" kimlikli uygulama bloğu ekle.
        byte[] evil = {0x21, (byte) 0xFF, 0x0B, 'E', 'V', 'I', 'L', '1', '2', '3', '4', 'A', 'B', 'C', 0x02, 'h', 'i', 0x00};
        int insertAt = 6 + 7 + 6; // başlık + ekran tanımlayıcı + 2 renkli tablo
        byte[] withEvil = new byte[gif.length + evil.length];
        System.arraycopy(gif, 0, withEvil, 0, insertAt);
        System.arraycopy(evil, 0, withEvil, insertAt, evil.length);
        System.arraycopy(gif, insertAt, withEvil, insertAt + evil.length, gif.length - insertAt);

        GifSanitizer.Result result = GifSanitizer.sanitize(withEvil, 100);

        assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("EVIL1234ABC");
        assertThat(result.bytes()).isEqualTo(gif);
    }

    @Test
    void kareSiniriAsilirsaReddedilir() {
        assertThat(GifSanitizer.sanitize(TestImages.gif(100, false, 8, 8), 100).frames()).isEqualTo(100);
        assertThatThrownBy(() -> GifSanitizer.sanitize(TestImages.gif(101, false, 8, 8), 100))
                .isInstanceOf(GifSanitizer.InvalidGifException.class);
    }

    @Test
    void bozukYapilarReddedilir() {
        byte[] valid = TestImages.gif(1, false, 8, 8);
        // Kesik dosya: her uzunlukta kesilen gif reddedilmeli (hiçbiri sınır dışı okuma yapmamalı).
        for (int length = 0; length < valid.length; length++) {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThatThrownBy(() -> GifSanitizer.sanitize(truncated, 100))
                    .isInstanceOf(GifSanitizer.InvalidGifException.class);
        }
        byte[] noTrailer = Arrays.copyOf(valid, valid.length - 1);
        assertThatThrownBy(() -> GifSanitizer.sanitize(noTrailer, 100)).isInstanceOf(GifSanitizer.InvalidGifException.class);
        byte[] unknownBlock = valid.clone();
        unknownBlock[unknownBlock.length - 1] = 0x55;
        assertThatThrownBy(() -> GifSanitizer.sanitize(unknownBlock, 100))
                .isInstanceOf(GifSanitizer.InvalidGifException.class);
        assertThatThrownBy(() -> GifSanitizer.sanitize("GIF89a".getBytes(StandardCharsets.US_ASCII), 100))
                .isInstanceOf(GifSanitizer.InvalidGifException.class);
    }

    @Test
    void karesizVeSifirBoyutluGifReddedilir() {
        assertThatThrownBy(() -> GifSanitizer.sanitize(TestImages.gif(0, false, 8, 8), 100))
                .isInstanceOf(GifSanitizer.InvalidGifException.class);
        assertThatThrownBy(() -> GifSanitizer.sanitize(TestImages.gif(1, false, 0, 8), 100))
                .isInstanceOf(GifSanitizer.InvalidGifException.class);
    }

    @Test
    void imzaDenetimi() {
        assertThat(GifSanitizer.hasGifSignature(TestImages.gif(1, false, 1, 1))).isTrue();
        assertThat(GifSanitizer.hasGifSignature("GIF87a".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(GifSanitizer.hasGifSignature("GIF90a".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(GifSanitizer.hasGifSignature(new byte[] {'G', 'I'})).isFalse();
    }
}
