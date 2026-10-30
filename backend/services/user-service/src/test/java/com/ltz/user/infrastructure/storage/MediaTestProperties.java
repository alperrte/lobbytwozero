package com.ltz.user.infrastructure.storage;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.util.unit.DataSize;

import com.ltz.user.infrastructure.config.MediaProperties;

/** Birim testleri için {@link MediaProperties} (varsayılan üretim sınırları). */
final class MediaTestProperties {

    private MediaTestProperties() {
    }

    static MediaProperties withRoot(Path root) {
        return with(root, 2, Duration.ofSeconds(3));
    }

    static MediaProperties with(Path root, int concurrency, Duration busyWait) {
        return new MediaProperties(root, DataSize.ofMegabytes(2), DataSize.ofMegabytes(4), 512, 128, 1600, 800, 100,
                8000, 40_000_000L, concurrency, busyWait, 10, false, Duration.ofMinutes(5));
    }
}
