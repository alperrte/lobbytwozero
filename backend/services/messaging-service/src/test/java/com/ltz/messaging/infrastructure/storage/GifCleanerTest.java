package com.ltz.messaging.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestImages;

/** GIF blok temizliği: kareler ve döngü korunur, yorum/uygulama blokları ve kapanış sonrası baytlar atılır. */
class GifCleanerTest {

    private static final byte[] HEADER_AND_SCREEN = {
            'G', 'I', 'F', '8', '9', 'a', 2, 0, 2, 0, 0, 0, 0};

    @Test
    void karelerKorunurGizliBloklarVeSonrakiBaytlarAtilir() throws IOException {
        byte[] gif = TestImages.animatedGif(3, 20, 10, "<script>alert(1)</script>");

        GifCleaner.Result result = GifCleaner.clean(gif, 10);

        assertThat(result.frames()).isEqualTo(3);
        assertThat(result.width()).isEqualTo(20);
        assertThat(result.height()).isEqualTo(10);
        assertThat(TestImages.contains(result.bytes(), TestImages.SECRET_MARKER)).isFalse();
        assertThat(TestImages.contains(result.bytes(), "<script>")).isFalse();
        assertThat(result.bytes()[result.bytes().length - 1]).isEqualTo((byte) 0x3B);
        assertThat(frameCount(result.bytes())).isEqualTo(3);
    }

    @Test
    void karSiniriAsilirsaReddedilir() {
        byte[] gif = TestImages.animatedGif(4, 4, 4, "");

        assertThatThrownBy(() -> GifCleaner.clean(gif, 3)).isInstanceOf(GifCleaner.MalformedGifException.class);
    }

    @Test
    void imzaYoksaKesikseYaDaBilinmeyenBlokVarsaReddedilir() {
        byte[] gif = TestImages.animatedGif(2, 4, 4, "");
        byte[] truncated = Arrays.copyOf(gif, gif.length / 2);
        byte[] unknownBlock = concat(HEADER_AND_SCREEN, new byte[] {0x55, 0x3B});

        assertThat(GifCleaner.hasSignature("<svg></svg>".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThatThrownBy(() -> GifCleaner.clean("<svg/>".getBytes(StandardCharsets.US_ASCII), 10))
                .isInstanceOf(GifCleaner.MalformedGifException.class);
        assertThatThrownBy(() -> GifCleaner.clean(truncated, 10))
                .isInstanceOf(GifCleaner.MalformedGifException.class);
        assertThatThrownBy(() -> GifCleaner.clean(unknownBlock, 10))
                .isInstanceOf(GifCleaner.MalformedGifException.class);
    }

    @Test
    void karesizGifReddedilir() {
        byte[] noFrames = concat(HEADER_AND_SCREEN, new byte[] {0x3B});

        assertThatThrownBy(() -> GifCleaner.clean(noFrames, 10)).isInstanceOf(GifCleaner.MalformedGifException.class);
    }

    @Test
    void dongudisiUygulamaBloguAtilirDonguBloguKorunur() {
        byte[] xmp = concat(new byte[] {0x21, (byte) 0xFF, 11}, "XMP DataXMP".getBytes(StandardCharsets.US_ASCII),
                new byte[] {3, 'a', 'b', 'c', 0});
        byte[] loop = concat(new byte[] {0x21, (byte) 0xFF, 11}, "NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII),
                new byte[] {3, 1, 0, 0, 0});
        byte[] frame = {0x2C, 0, 0, 0, 0, 2, 0, 2, 0, 0, 2, 2, 0x44, 0x01, 0};
        byte[] gif = concat("GIF87a".getBytes(StandardCharsets.US_ASCII), new byte[] {2, 0, 2, 0, (byte) 0x80, 0, 0},
                new byte[6], xmp, loop, frame, new byte[] {0x3B});

        GifCleaner.Result result = GifCleaner.clean(gif, 10);

        assertThat(result.frames()).isEqualTo(1);
        assertThat(TestImages.contains(result.bytes(), "XMP")).isFalse();
        assertThat(TestImages.contains(result.bytes(), "NETSCAPE2.0")).isTrue();
        assertThat(result).isEqualTo(GifCleaner.clean(gif, 10)).hasSameHashCodeAs(GifCleaner.clean(gif, 10));
        assertThat(result.toString()).contains("2x2").contains("frames=1");
    }

    private static int frameCount(byte[] gif) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
            reader.setInput(input);
            return reader.getNumImages(true);
        } finally {
            reader.dispose();
        }
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        byte[] result = new byte[length];
        int position = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, position, part.length);
            position += part.length;
        }
        return result;
    }
}
