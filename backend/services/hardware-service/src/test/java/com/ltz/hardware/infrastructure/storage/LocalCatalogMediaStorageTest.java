package com.ltz.hardware.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.hardware.infrastructure.config.IcecatProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCatalogMediaStorageTest {
    @TempDir private Path directory;

    private LocalCatalogMediaStorage storage(boolean enabled, boolean rights) {
        var properties = new IcecatProperties();
        properties.setLocalMediaEnabled(enabled);
        properties.setMediaRightsVerified(rights);
        properties.setMediaDirectory(directory);
        return new LocalCatalogMediaStorage(properties);
    }

    private static byte[] image(int width, int height) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    @Test
    void storesReencodedImageAndReadsOnlyValidatedKey() throws Exception {
        var storage = storage(true, true);
        var stored = storage.store(image(3, 2));
        assertThat(stored.width()).isEqualTo(3);
        assertThat(stored.height()).isEqualTo(2);
        assertThat(stored.key()).isEqualTo(stored.hash() + ".png");
        assertThat(storage.read(stored.key())).isEqualTo(Files.readAllBytes(directory.resolve(stored.key())));
        try (var files = Files.list(directory)) {
            assertThat(files.map(Path::getFileName).map(Path::toString).toList()).containsExactly(stored.key());
        }
        assertThatThrownBy(() -> storage.read("../outside.png")).hasMessage("MEDIA_KEY");
        assertThatThrownBy(() -> storage.read("a".repeat(64) + ".png")).hasMessage("MEDIA_UNAVAILABLE");
    }

    @Test
    void rejectsDisabledUnlicensedOversizedAndUnsupportedImages() {
        assertThat(storage(false, true).available()).isFalse();
        assertThat(storage(true, false).available()).isFalse();
        assertThatThrownBy(() -> storage(false, true).store(new byte[0])).hasMessage("MEDIA_DISABLED_OR_TOO_LARGE");
        var storage = storage(true, true);
        assertThatThrownBy(() -> storage.store(new byte[5_000_001])).hasMessage("MEDIA_DISABLED_OR_TOO_LARGE");
        assertThatThrownBy(() -> storage.store(new byte[] {1, 2, 3})).hasMessage("MEDIA_UNSUPPORTED");
    }

    @Test
    void rejectsDimensionAndPixelLimitsBeforeDecoding() throws Exception {
        var storage = storage(true, true);
        byte[] wide = image(4097, 1);
        byte[] tall = image(1, 4097);
        byte[] pixels = image(2001, 2000);
        assertThatThrownBy(() -> storage.store(wide)).hasMessage("MEDIA_DIMENSIONS");
        assertThatThrownBy(() -> storage.store(tall)).hasMessage("MEDIA_DIMENSIONS");
        assertThatThrownBy(() -> storage.store(pixels)).hasMessage("MEDIA_DIMENSIONS");
    }
}
