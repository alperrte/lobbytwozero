package com.ltz.messaging.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestImages;

/** EXIF yön etiketi: iki bayt sırası, eksik/bozuk veri ve sınır dışı okumalar. */
class JpegOrientationTest {

    private final byte[] plain = TestImages.jpeg(8, 4);

    @Test
    void ikiBaytSirasindaDaYonOkunur() {
        assertThat(JpegOrientation.of(TestImages.withExif(plain, 6, true))).isEqualTo(6);
        assertThat(JpegOrientation.of(TestImages.withExif(plain, 3, false))).isEqualTo(3);
        assertThat(JpegOrientation.of(TestImages.withExif(plain, 8, true))).isEqualTo(8);
    }

    @Test
    void exifYoksaYaDaDegerGecersizseDonusumYok() {
        assertThat(JpegOrientation.of(plain)).isEqualTo(JpegOrientation.NONE);
        assertThat(JpegOrientation.of(TestImages.withExif(plain, 9, true))).isEqualTo(JpegOrientation.NONE);
        assertThat(JpegOrientation.of(TestImages.withExif(plain, 0, false))).isEqualTo(JpegOrientation.NONE);
    }

    @Test
    void kesikYaDaBozukVeriSinirDisinaOkumaz() {
        byte[] withExif = TestImages.withExif(plain, 6, true);
        int exifEnd = 4 + ((withExif[4] & 0xFF) << 8 | withExif[5] & 0xFF);
        for (int length = 0; length < exifEnd; length++) {
            assertThat(JpegOrientation.of(Arrays.copyOf(withExif, length))).isEqualTo(JpegOrientation.NONE);
        }
        byte[] badOrder = withExif.clone();
        badOrder[12] = 'X'; // TIFF bayt sırası: SOI (2) + APP1 başlığı (4) + EXIF kimliği (6) sonrası
        assertThat(JpegOrientation.of(badOrder)).isEqualTo(JpegOrientation.NONE);
        byte[] farIfd = withExif.clone();
        farIfd[19] = 0x7F; // ilk IFD konumunun en yüksek baytı: bölümün dışı
        assertThat(JpegOrientation.of(farIfd)).isEqualTo(JpegOrientation.NONE);
        byte[] badLength = withExif.clone();
        badLength[5] = 1; // bölüm uzunluğu 2'den küçük
        assertThat(JpegOrientation.of(badLength)).isEqualTo(JpegOrientation.NONE);
    }
}
