package com.ltz.messaging.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestImages;

/**
 * EXIF yönlerinin (1-8) doğru uygulanması: 4x2 görselde saklanan sol üst (0,0) kırmızı piksel, düzeltilmiş görselde
 * EXIF tanımına göre beklenen yere gider; 5-8'de en ve boy yer değiştirir.
 */
class RasterImagesTest {

    @ParameterizedTest(name = "yön {0}")
    @CsvSource({
            "1, 4, 2, 0, 0",
            "2, 4, 2, 3, 0",
            "3, 4, 2, 3, 1",
            "4, 4, 2, 0, 1",
            "5, 2, 4, 0, 0",
            "6, 2, 4, 1, 0",
            "7, 2, 4, 1, 3",
            "8, 2, 4, 0, 3"
    })
    void yonEtiketiUygulanir(int orientation, int width, int height, int redX, int redY) {
        BufferedImage oriented = RasterImages.orient(TestImages.markedCorner(4, 2), orientation);

        assertThat(oriented.getWidth()).isEqualTo(width);
        assertThat(oriented.getHeight()).isEqualTo(height);
        assertThat(oriented.getRGB(redX, redY)).isEqualTo(Color.RED.getRGB());
        int redPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                redPixels += oriented.getRGB(x, y) == Color.RED.getRGB() ? 1 : 0;
            }
        }
        assertThat(redPixels).isEqualTo(1);
    }

    @Test
    void kucultmeOraniKorurVeEnAzBirPikselBirakir() {
        BufferedImage source = TestImages.markedCorner(400, 100);

        assertThat(RasterImages.shrink(source, 0.5).getWidth()).isEqualTo(200);
        assertThat(RasterImages.shrink(source, 0.5).getHeight()).isEqualTo(50);
        assertThat(RasterImages.shrink(source, 0.001).getHeight()).isEqualTo(1);
    }

    @Test
    void saydamGorselJpegeBeyazZeminleKodlanir() throws IOException {
        BufferedImage transparent = ImageIO.read(new ByteArrayInputStream(TestImages.transparentPng(10, 10)));

        BufferedImage jpeg = ImageIO.read(new ByteArrayInputStream(RasterImages.encode(transparent, false)));
        BufferedImage png = ImageIO.read(new ByteArrayInputStream(RasterImages.encode(transparent, true)));

        assertThat(new Color(jpeg.getRGB(1, 5)).getRed()).isGreaterThan(240);
        assertThat(png.getRGB(1, 5) >>> 24).isZero();
    }
}
