package com.ltz.messaging.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestImages;
import com.ltz.messaging.application.port.ImageInspectorPort.ProcessedImage;
import com.ltz.messaging.domain.entity.MessageAttachment;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.AttachmentProperties;

/**
 * Görsel doğrulama (MSG-17, SECURITY.md §13): biçim içerikten belirlenir ve bildirilen türle aynı olmalı; boyut/piksel/kare
 * sınırları; EXIF ve gömülü veri atılır, JPEG yönü uygulanır; 1 MB'ı aşan yeniden kodlama küçültülür.
 */
class ImageIoInspectorTest {

    private final ImageIoInspector inspector = new ImageIoInspector(properties());

    private static AttachmentProperties properties() {
        return new AttachmentProperties(10, 10, Duration.ofHours(1), 4096, 12_582_912L, 64, 10, 20_000L, 2,
                Duration.ofSeconds(2));
    }

    @Test
    void bicimIcerikteBelirlenir() {
        assertThat(ImageIoInspector.detect(TestImages.png(2, 2))).isEqualTo(ImageIoInspector.PNG);
        assertThat(ImageIoInspector.detect(TestImages.jpeg(2, 2))).isEqualTo(ImageIoInspector.JPEG);
        assertThat(ImageIoInspector.detect(TestImages.animatedGif(1, 2, 2, ""))).isEqualTo(ImageIoInspector.GIF);
        assertThat(ImageIoInspector.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)))
                .isNull();
        assertThat(ImageIoInspector.detect(new byte[] {(byte) 0x89, 'P'})).isNull();
    }

    @Test
    void bildirilenTurIcerikleUyusmazsaVeyaBilinmiyorsaReddedilir() {
        byte[] png = TestImages.png(4, 4);

        assertCode(() -> inspector.process(png, "image/gif"), MessagingErrorCode.INVALID_IMAGE);
        assertCode(() -> inspector.process(png, null), MessagingErrorCode.INVALID_IMAGE);
        assertCode(() -> inspector.process("<html></html>".getBytes(StandardCharsets.UTF_8), "image/png"),
                MessagingErrorCode.INVALID_IMAGE);
        assertCode(() -> inspector.process(new byte[0], "image/png"), MessagingErrorCode.INVALID_IMAGE);
        assertCode(() -> inspector.process(null, "image/png"), MessagingErrorCode.INVALID_IMAGE);
        assertThat(inspector.process(png, "IMAGE/PNG; charset=binary").mimeType()).isEqualTo("image/png");
    }

    @Test
    void bozukVeKesikGorselReddedilir() {
        byte[] png = TestImages.png(40, 40);
        byte[] jpeg = TestImages.jpeg(40, 40);

        assertCode(() -> inspector.process(Arrays.copyOf(png, png.length / 2), "image/png"),
                MessagingErrorCode.INVALID_IMAGE);
        assertCode(() -> inspector.process(Arrays.copyOf(jpeg, 20), "image/jpeg"), MessagingErrorCode.INVALID_IMAGE);
        assertCode(() -> inspector.process(Arrays.copyOf(TestImages.animatedGif(2, 4, 4, ""), 30), "image/gif"),
                MessagingErrorCode.INVALID_IMAGE);
    }

    @Test
    void boyutVePikselSinirlari() {
        assertCode(() -> inspector.process(new byte[MessageAttachment.MAX_SIZE_BYTES + 1], "image/png"),
                MessagingErrorCode.IMAGE_TOO_LARGE);
        assertCode(() -> inspector.process(TestImages.png(5000, 2), "image/png"),
                MessagingErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
        // GIF: kenar (64), kenar x kenar x kare (20.000) ve kare (10) sınırı.
        assertCode(() -> inspector.process(TestImages.animatedGif(1, 65, 2, ""), "image/gif"),
                MessagingErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
        assertCode(() -> inspector.process(TestImages.animatedGif(6, 60, 60, ""), "image/gif"),
                MessagingErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
        assertCode(() -> inspector.process(TestImages.animatedGif(11, 2, 2, ""), "image/gif"),
                MessagingErrorCode.INVALID_IMAGE);
    }

    @Test
    void jpegYonuUygulanirExifVeYorumAtilir() throws IOException {
        byte[] upload = TestImages.withExif(TestImages.jpeg(40, 20), 6, false);

        ProcessedImage image = inspector.process(upload, "image/jpeg");

        assertThat(image.width()).isEqualTo(20);
        assertThat(image.height()).isEqualTo(40);
        assertThat(TestImages.contains(image.content(), "Exif")).isFalse();
        assertThat(TestImages.contains(image.content(), TestImages.SECRET_MARKER)).isFalse();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.content()));
        assertThat(decoded.getWidth()).isEqualTo(20);
        assertThat(image).isEqualTo(new ProcessedImage(image.mimeType(), image.content().clone(), 20, 40, 1))
                .hasSameHashCodeAs(new ProcessedImage(image.mimeType(), image.content().clone(), 20, 40, 1));
        assertThat(image.toString()).contains("image/jpeg").contains("20x40").doesNotContain("[B@");
    }

    @Test
    void pngSaydamligiKorunur() throws IOException {
        ProcessedImage image = inspector.process(TestImages.transparentPng(10, 10), "image/png");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.content()));
        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(decoded.getRGB(1, 1) >>> 24).isZero();
        assertThat(decoded.getRGB(8, 1) >>> 24).isEqualTo(0xFF);
    }

    @Test
    void yenidenKodlamaBirMegabaytiAsarsaKucultulur() {
        byte[] upload = TestImages.noisyJpeg(2000, 2000, 0.1f);
        assertThat(upload.length).isLessThanOrEqualTo(MessageAttachment.MAX_SIZE_BYTES);

        ProcessedImage image = inspector.process(upload, "image/jpeg");

        assertThat(image.content().length).isLessThanOrEqualTo(MessageAttachment.MAX_SIZE_BYTES);
        assertThat(image.width()).isLessThan(2000);
        assertThat(image.width()).isEqualTo(image.height());
    }

    @Test
    void gifAnimasyonuKorunurGizliVeriAtilir() {
        ProcessedImage image = inspector.process(TestImages.animatedGif(3, 20, 10, "<?php echo 1; ?>"), "image/gif");

        assertThat(image.frames()).isEqualTo(3);
        assertThat(image.mimeType()).isEqualTo("image/gif");
        assertThat(TestImages.contains(image.content(), "<?php")).isFalse();
        assertThat(TestImages.contains(image.content(), TestImages.SECRET_MARKER)).isFalse();
    }

    @Test
    void islemeYuvasiBosalmazsaMesgulDonerYuvaSizdirilmaz() {
        AttachmentProperties shortWait = new AttachmentProperties(10, 10, Duration.ofHours(1), 4096, 12_582_912L, 64,
                10, 20_000L, 1, Duration.ofMillis(50));
        Semaphore slots = new Semaphore(1);
        ImageIoInspector busy = new ImageIoInspector(shortWait, slots);
        slots.acquireUninterruptibly();

        assertCode(() -> busy.process(TestImages.png(2, 2), "image/png"), MessagingErrorCode.MEDIA_BUSY);

        slots.release();
        assertThat(busy.process(TestImages.png(2, 2), "image/png").width()).isEqualTo(2);
        // Hatalı görselde de yuva geri verilir.
        assertCode(() -> busy.process(Arrays.copyOf(TestImages.png(8, 8), 30), "image/png"),
                MessagingErrorCode.INVALID_IMAGE);
        assertThat(slots.availablePermits()).isEqualTo(1);
    }

    private static void assertCode(ThrowingCallable call, MessagingErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(MessagingException.class)
                .satisfies(error -> assertThat(((MessagingException) error).errorCode()).isEqualTo(code));
    }
}
