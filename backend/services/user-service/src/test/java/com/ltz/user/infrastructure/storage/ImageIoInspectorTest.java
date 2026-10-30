package com.ltz.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.user.TestImages;
import com.ltz.user.application.port.ImageInspectorPort.ProcessedImage;
import com.ltz.user.domain.enums.MediaKind;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;

/** Görsel işleme boru hattı (veritabanı gerektirmez). */
class ImageIoInspectorTest {

    private final ImageIoInspector inspector = new ImageIoInspector(MediaTestProperties.withRoot(Path.of("unused")));

    @Test
    void gercekBicimIlkBaytlardanBelirlenir() {
        assertThat(ImageIoInspector.detect(TestImages.png(8, 8))).isEqualTo("image/png");
        assertThat(ImageIoInspector.detect(TestImages.jpeg(8, 8))).isEqualTo("image/jpeg");
        assertThat(ImageIoInspector.detect(TestImages.gif(1, false, 1, 1))).isEqualTo("image/gif");
        assertThat(ImageIoInspector.detect("<svg/>".getBytes())).isNull();
        assertThat(ImageIoInspector.detect(new byte[0])).isNull();
    }

    @Test
    void bildirilenTurParametreVeBuyukHarfeDuyarsiz() {
        ProcessedImage image = inspector.process(MediaKind.AVATAR, TestImages.png(64, 64), " IMAGE/PNG ; charset=binary");

        assertThat(image.contentType()).isEqualTo("image/jpeg");
        assertThat(image.extension()).isEqualTo("jpg");
        assertThat(image.frameCount()).isEqualTo(1);
    }

    @Test
    void yenidenKodlamaMetaVeriyiTemizlerJpegIcinExifYoktur() {
        ProcessedImage image = inspector.process(MediaKind.COVER, TestImages.jpegWithOrientation(400, 200, 3),
                "image/jpeg");

        // Çıktı yeniden kodlandığı için giriş EXIF'i (Exif başlığı) çıktıda bulunmaz.
        assertThat(new String(image.large(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        assertThat(image.width()).isEqualTo(400);
        assertThat(image.height()).isEqualTo(200);
    }

    @Test
    void ciktiAvatardaKareKapaktaOranKorunur() {
        ProcessedImage avatar = inspector.process(MediaKind.AVATAR, TestImages.png(1200, 300), "image/png");
        ProcessedImage cover = inspector.process(MediaKind.COVER, TestImages.png(3200, 800), "image/png");

        assertThat(avatar.width()).isEqualTo(300);
        assertThat(avatar.height()).isEqualTo(300);
        assertThat(cover.width()).isEqualTo(1600);
        assertThat(cover.height()).isEqualTo(400);
        assertThat(TestImages.read(cover.small()).getWidth()).isEqualTo(800);
    }

    @Test
    void tumEkseniEnBuyukKenariAsanGorselKapaktaSigdirilir() {
        ProcessedImage cover = inspector.process(MediaKind.COVER, TestImages.png(1000, 4000), "image/png");

        assertThat(cover.height()).isEqualTo(1600);
        assertThat(cover.width()).isEqualTo(400);
    }

    @Test
    void sunumSirasiDolarsaMediaBusy() {
        ImageIoInspector busy = new ImageIoInspector(MediaTestProperties.with(Path.of("unused"), 1,
                Duration.ofMillis(20)));
        Semaphore slots = (Semaphore) ReflectionTestUtils.getField(busy, "slots");
        slots.drainPermits();

        assertThatThrownBy(() -> busy.process(MediaKind.AVATAR, TestImages.png(64, 64), "image/png"))
                .isInstanceOfSatisfying(UserException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UserErrorCode.MEDIA_BUSY));
    }

    @Test
    void hataSonrasiSiraBosaltilir() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> inspector.process(MediaKind.AVATAR, new byte[] {1, 2, 3}, "image/png"))
                    .isInstanceOf(UserException.class);
        }

        // Yuva sızıntısı olsaydı (concurrency=2) buradaki geçerli görsel MEDIA_BUSY alırdı.
        assertThat(inspector.process(MediaKind.AVATAR, TestImages.png(64, 64), "image/png")).isNotNull();
    }
}
