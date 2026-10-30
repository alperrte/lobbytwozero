package com.ltz.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ltz.user.TestImages;

/** EXIF yönlendirme okuyucusu: geçerli değerler okunur, bozuk/kısa veri güvenle {@code 1} döner. */
class ExifOrientationTest {

    @Test
    void sekizYonlendirmeDegeriOkunur() {
        for (int orientation = 1; orientation <= 8; orientation++) {
            assertThat(ExifOrientation.read(TestImages.jpegWithOrientation(20, 10, orientation)))
                    .isEqualTo(orientation);
        }
    }

    @Test
    void exifOlmayanJpegNormalSayilir() {
        assertThat(ExifOrientation.read(TestImages.jpeg(20, 10))).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    void gecersizDegerNormalSayilir() {
        assertThat(ExifOrientation.read(TestImages.jpegWithOrientation(20, 10, 0))).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.read(TestImages.jpegWithOrientation(20, 10, 9))).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    void kesilmisVeBozukVeriHicbirZamanSinirDisinaTasmaz() {
        byte[] valid = TestImages.jpegWithOrientation(20, 10, 6);
        for (int length = 0; length < 80; length++) {
            assertThat(ExifOrientation.read(Arrays.copyOf(valid, length))).isBetween(1, 8);
        }
        assertThat(ExifOrientation.read(new byte[0])).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.read(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, (byte) 0xFF,
                (byte) 0xFF})).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    void kucukEndianTiffDeOkunur() {
        byte[] jpeg = TestImages.jpegWithOrientation(20, 10, 3);
        // "MM" → "II": ofset ve girdi alanlarını küçük endian'a çevir (6 = Exif\0\0 sonrası TIFF başı = 4 + 6 + 2).
        int tiff = 2 + 4 + 6;
        jpeg[tiff] = 'I';
        jpeg[tiff + 1] = 'I';
        jpeg[tiff + 2] = 0x2A;
        jpeg[tiff + 3] = 0x00;
        jpeg[tiff + 4] = 0x08;
        jpeg[tiff + 5] = 0x00;
        jpeg[tiff + 6] = 0x00;
        jpeg[tiff + 7] = 0x00;
        jpeg[tiff + 8] = 0x01;
        jpeg[tiff + 9] = 0x00;
        jpeg[tiff + 10] = 0x12;
        jpeg[tiff + 11] = 0x01;
        jpeg[tiff + 12] = 0x03;
        jpeg[tiff + 13] = 0x00;
        jpeg[tiff + 14] = 0x01;
        jpeg[tiff + 15] = 0x00;
        jpeg[tiff + 16] = 0x00;
        jpeg[tiff + 17] = 0x00;
        jpeg[tiff + 18] = 0x03;
        jpeg[tiff + 19] = 0x00;

        assertThat(ExifOrientation.read(jpeg)).isEqualTo(3);
    }
}
