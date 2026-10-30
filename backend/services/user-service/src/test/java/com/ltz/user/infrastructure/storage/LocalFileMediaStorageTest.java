package com.ltz.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import com.ltz.user.application.port.MediaStorageException;

/** Yerel depolama: atomik yazma, idempotent silme ve yol atlatma (path traversal) koruması. */
class LocalFileMediaStorageTest {

    private static final String NAME = "0123456789abcdef0123456789abcdef.jpg";

    @TempDir
    Path root;

    private LocalFileMediaStorage storage() {
        return new LocalFileMediaStorage(MediaTestProperties.withRoot(root));
    }

    @Test
    void yazilanDosyaOkunurVeIlkIkiKarakterKlasorundedir() throws IOException {
        LocalFileMediaStorage storage = storage();

        storage.write(NAME, new byte[] {1, 2, 3});

        Resource resource = storage.open(NAME).orElseThrow();
        assertThat(resource.getContentAsByteArray()).containsExactly(1, 2, 3);
        assertThat(root.resolve("01").resolve(NAME)).exists();
    }

    @Test
    void uzerineYazmaAtomikVeGeciciDosyaKalmaz() throws IOException {
        LocalFileMediaStorage storage = storage();
        storage.write(NAME, new byte[] {1});

        storage.write(NAME, new byte[] {9, 9});

        assertThat(storage.open(NAME).orElseThrow().getContentAsByteArray()).containsExactly(9, 9);
        try (Stream<Path> files = Files.list(root.resolve("01"))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly(NAME);
        }
    }

    @Test
    void silmeIdempotent() {
        LocalFileMediaStorage storage = storage();
        storage.write(NAME, new byte[] {1});

        storage.delete(NAME);
        storage.delete(NAME);

        assertThat(storage.open(NAME)).isEmpty();
    }

    @Test
    void gecersizAdlarYazilamazSilinemezAcilamaz() {
        LocalFileMediaStorage storage = storage();
        String[] invalid = {"../" + NAME, "..\\" + NAME, "/etc/passwd", "a.jpg", NAME.toUpperCase(),
                "0123456789abcdef0123456789abcdef.svg", "0123456789abcdef0123456789abcdef.jpg/../x", "", "%2e%2e"};

        for (String name : invalid) {
            assertThatThrownBy(() -> storage.write(name, new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete(name)).isInstanceOf(IllegalArgumentException.class);
            assertThat(storage.open(name)).isEmpty();
        }
        assertThatThrownBy(() -> storage.write(null, new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kokKlasorYazilamazsaStorageExceptionDoner() throws IOException {
        Path blocker = Files.createFile(root.resolve("not-a-directory"));
        LocalFileMediaStorage storage = new LocalFileMediaStorage(MediaTestProperties.withRoot(blocker.resolve("sub")));

        assertThatThrownBy(() -> storage.write(NAME, new byte[] {1})).isInstanceOf(MediaStorageException.class);
        assertThat(Paths.get(root.toString())).exists();
    }
}
