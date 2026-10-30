package com.ltz.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.user.IntegrationTestSupport;
import com.ltz.user.application.port.MediaStoragePort;

/** Silinmeyi bekleyen görsellerin temizliği: dosya hemen, satır yükleme sayacı penceresi dolunca silinir. */
class MediaCleanupServiceTest extends IntegrationTestSupport {

    @Autowired
    private MediaCleanupService cleanup;

    @Autowired
    private MediaStoragePort storage;

    @Test
    void bekleyenGorselinDosyasiSilinirSatirPencereDolanaKadarKalir() {
        UUID user = newUser();
        insertProfile(user);
        String recent = insertMedia(user, "AVATAR", "PENDING_DELETE", "0 minutes");
        String old = insertMedia(user, "COVER", "PENDING_DELETE", "2 hours");
        String current = insertMedia(user, "AVATAR", "CURRENT", "3 hours");

        int removed = cleanup.purge();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(storage.open(recent + ".jpg")).as("bekleyen görselin dosyası silinmiş olmalı").isEmpty();
        assertThat(storage.open(old + ".jpg")).isEmpty();
        assertThat(storage.open(current + ".jpg")).as("güncel görsele dokunulmaz").isPresent();
        assertThat(rows(user, "PENDING_DELETE", recent)).as("sayaç penceresi dolmadı: satır kalır").isEqualTo(1);
        assertThat(rows(user, "PENDING_DELETE", old)).as("pencere doldu: satır silinir").isZero();
        assertThat(rows(user, "CURRENT", current)).isEqualTo(1);
    }

    @Test
    void ikinciTurIdempotent() {
        UUID user = newUser();
        insertProfile(user);
        String key = insertMedia(user, "AVATAR", "PENDING_DELETE", "2 hours");

        cleanup.purge();
        cleanup.purge();

        assertThat(rows(user, "PENDING_DELETE", key)).isZero();
    }

    @Test
    void silinemeyenDosyaninSatiriKorunurSonrakiTurdaYenidenDenenir() throws Exception {
        UUID user = newUser();
        insertProfile(user);
        String key = UUID.randomUUID().toString().replace("-", "");
        // Dosya adının bulunduğu yola boş olmayan klasör koy: silme IOException verir.
        Path blocked = MEDIA_ROOT.resolve(key.substring(0, 2)).resolve(key + ".jpg");
        Files.createDirectories(blocked);
        Files.createFile(blocked.resolve("child"));
        insertRow(user, "AVATAR", "PENDING_DELETE", "2 hours", key);

        cleanup.purge();

        assertThat(rows(user, "PENDING_DELETE", key)).as("dosya silinemedi: satır korunur").isEqualTo(1);

        Files.delete(blocked.resolve("child"));
        Files.delete(blocked);
        cleanup.purge();
        assertThat(rows(user, "PENDING_DELETE", key)).isZero();
    }

    /** Diskte dosyaları olan bir görsel satırı ekler. */
    private String insertMedia(UUID user, String kind, String status, String age) {
        String key = UUID.randomUUID().toString().replace("-", "");
        storage.write(key + ".jpg", new byte[] {1, 2, 3});
        storage.write(key + "-s.jpg", new byte[] {1});
        insertRow(user, kind, status, age, key);
        return key;
    }

    private void insertRow(UUID user, String kind, String status, String age, String key) {
        jdbc.update("""
                insert into user_media (id, user_id, kind, storage_key, content_type, byte_size, width, height, status,
                                        created_at)
                values (?, ?, ?, ?, 'image/jpeg', 4, 10, 10, ?, now() - ?::interval)""",
                UUID.randomUUID(), user, kind, key, status, age);
    }

    private int rows(UUID user, String status, String key) {
        return jdbc.queryForObject("select count(*) from user_media where user_id = ? and status = ? and storage_key = ?",
                Integer.class, user, status, key);
    }
}
