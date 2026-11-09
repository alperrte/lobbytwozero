package com.ltz.hardware.infrastructure.storage;

import com.ltz.hardware.application.port.CatalogMediaStorage;
import com.ltz.hardware.infrastructure.config.IcecatProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.springframework.stereotype.Component;

/** Explicit local-development adapter. No production storage service is provisioned. */
@Component
public class LocalCatalogMediaStorage implements CatalogMediaStorage {
    private final IcecatProperties properties;
    public LocalCatalogMediaStorage(IcecatProperties properties) { this.properties = properties; }
    @Override public boolean available() { return properties.isLocalMediaEnabled() && properties.isMediaRightsVerified(); }
    @Override public Stored store(byte[] input) {
        if (!available() || input.length > 5_000_000) throw new IllegalArgumentException("MEDIA_DISABLED_OR_TOO_LARGE");
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("MEDIA_UNSUPPORTED");
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                return storeImage(reader);
            } finally { reader.dispose(); }
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MEDIA_STORE_FAILED", exception);
        }
    }

    private Stored storeImage(ImageReader reader) throws IOException, NoSuchAlgorithmException {
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        requireDimensions(width, height);
        var pixels = reader.read(0);
        var buffer = new ByteArrayOutputStream();
        if (!ImageIO.write(pixels, "png", buffer)) throw new IOException("PNG encoder unavailable");
        var output = buffer.toByteArray();
        if (output.length > 5_000_000) throw new IllegalArgumentException("MEDIA_ENCODED_LIMIT");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(output));
        String key = hash + ".png";
        persist(key, output);
        return new Stored(key, hash, width, height);
    }

    private static void requireDimensions(int width, int height) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096
                || (long) width * height > 4_000_000) throw new IllegalArgumentException("MEDIA_DIMENSIONS");
    }

    private void persist(String key, byte[] output) throws IOException {
        Path root = directory();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("MEDIA_PATH");
        if (Files.isSymbolicLink(target)) throw new IllegalArgumentException("MEDIA_SYMLINK");
        var temporary = Files.createTempFile(root, "icecat-", ".tmp");
        try {
            Files.write(temporary, output);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }
    @Override public byte[] read(String key) {
        if (!available() || !key.matches("[a-f0-9]{64}\\.png")) throw new IllegalArgumentException("MEDIA_KEY");
        try {
            var root = directory();
            var target = root.resolve(key).normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(target) > 5_000_000) throw new IllegalArgumentException("MEDIA_UNAVAILABLE");
            return Files.readAllBytes(target);
        } catch (IOException exception) { throw new IllegalStateException("MEDIA_READ_FAILED", exception); }
    }
    private Path directory() throws IOException {
        Path root = properties.getMediaDirectory().toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IllegalArgumentException("MEDIA_ROOT_SYMLINK");
        return root.toRealPath();
    }
}
