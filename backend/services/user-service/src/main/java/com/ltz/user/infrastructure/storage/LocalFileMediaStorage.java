package com.ltz.user.infrastructure.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.ltz.user.application.port.MediaStorageException;
import com.ltz.user.application.port.MediaStoragePort;
import com.ltz.user.infrastructure.config.MediaProperties;

/**
 * Yerel diskte depolama. Dosyalar ilk iki anahtar karakteriyle bölünmüş klasörlere yazılır (tek klasörde milyonlarca
 * dosya olmasın). Yazma, aynı klasördeki geçici dosyaya yazıp atomik taşıma ile yapılır: yarım dosya hiçbir zaman
 * okunamaz. Dosya adı yalnız {@link #FILE_NAME} desenine uyuyorsa kabul edilir ve çözülen yol kök klasörün altında
 * kalmalıdır; yol atlatma (path traversal) bu iki denetimle engellenir.
 */
@Component
public class LocalFileMediaStorage implements MediaStoragePort {

    /** {@code <32 onaltılık anahtar>[-s].<jpg|png|gif>} */
    public static final Pattern FILE_NAME = Pattern.compile("^[0-9a-f]{32}(-s)?\\.(jpg|png|gif)$");

    private static final int SHARD_LENGTH = 2;

    private final Path root;

    public LocalFileMediaStorage(MediaProperties properties) {
        this.root = properties.root().toAbsolutePath().normalize();
    }

    @Override
    public void write(String fileName, byte[] content) {
        Path target = resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, content);
                move(temp, target);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException exception) {
            throw new MediaStorageException("Media file could not be written", exception);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException exception) {
            throw new MediaStorageException("Media file could not be deleted", exception);
        }
    }

    @Override
    public Optional<Resource> open(String fileName) {
        if (!FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path file = resolve(fileName);
        return Files.isRegularFile(file) ? Optional.of(new FileSystemResource(file)) : Optional.empty();
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Invalid media file name");
        }
        Path path = root.resolve(fileName.substring(0, SHARD_LENGTH)).resolve(fileName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid media file name");
        }
        return path;
    }
}
