package com.ltz.user.application.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.user.application.port.ImageInspectorPort;
import com.ltz.user.application.port.ImageInspectorPort.ProcessedImage;
import com.ltz.user.application.port.MediaStorageException;
import com.ltz.user.application.port.MediaStoragePort;
import com.ltz.user.domain.entity.UserMedia;
import com.ltz.user.domain.enums.AccountStatus;
import com.ltz.user.domain.enums.MediaKind;
import com.ltz.user.domain.enums.MediaStatus;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.config.MediaProperties;
import com.ltz.user.infrastructure.persistence.repository.UserMediaRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Profil görselleri (USER-03 avatar, USER-04 kapak). Dosya ve veritabanı tek transaction'da olamaz; tutarlılık şu sırayla
 * sağlanır: <b>1)</b> görsel işlenir, <b>2)</b> yeni dosyalar yazılır, <b>3)</b> tek DB transaction'ında eski satır
 * {@code PENDING_DELETE} olur ve yenisi {@code CURRENT} eklenir, <b>4)</b> commit sonrası eski dosyalar silinir. 3. adım
 * başarısız olursa yeni dosyalar geri silinir; 4. adım başarısız olursa satır {@code PENDING_DELETE} kalır ve
 * {@link MediaCleanupService} dosyayı yeniden dener. Hiçbir adımda profilde dosyası olmayan bir {@code CURRENT} satır
 * oluşmaz. Yalnız doğrulanmış kimliğin kendi görseli değiştirilir; kimlik isteğin başka hiçbir yerinden okunmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    /** Yükleme sayacı penceresi; {@code PENDING_DELETE} satırları da bu süre dolana kadar tutulur. */
    public static final Duration UPLOAD_WINDOW = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int KEY_BYTES = 16;

    private final ImageInspectorPort inspector;
    private final MediaStoragePort storage;
    private final UserMediaRepository media;
    private final UserProfileRepository profiles;
    private final ProfileService profileService;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final MediaProperties properties;

    /** Görseli doğrular, kaydeder ve güncel profili döner. */
    public ProfileView replace(UUID userId, MediaKind kind, byte[] content, String declaredContentType) {
        Instant now = clock.instant();
        requireWithinUploadLimit(userId, now);
        ProcessedImage image = inspector.process(kind, content, declaredContentType);
        UserMedia created = UserMedia.create(userId, kind, new UserMedia.Upload(newKey(), image.contentType(),
                size(image), image.width(), image.height(), image.frameCount()), now);

        List<String> written = writeFiles(created, image);
        List<UserMedia> replaced;
        try {
            replaced = transactions.execute(status -> swap(created, now));
        } catch (DataIntegrityViolationException _) {
            // Aynı kullanıcı aynı türü eşzamanlı yükledi: kısmi tekil indeks ikincisini reddeder.
            deleteQuietly(written);
            throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
        } catch (RuntimeException exception) {
            deleteQuietly(written);
            throw exception;
        }
        deleteFiles(replaced);
        return profileService.getOrProvision(userId);
    }

    /** Görseli kaldırır. Görsel yoksa da başarılıdır (idempotent). */
    public void remove(UUID userId, MediaKind kind) {
        List<UserMedia> removed = transactions.execute(status -> {
            List<UserMedia> current = media.findByUserIdAndKindAndStatus(userId, kind, MediaStatus.CURRENT);
            current.forEach(UserMedia::markPendingDelete);
            media.flush();
            return current;
        });
        deleteFiles(removed);
    }

    private List<UserMedia> swap(UserMedia created, Instant now) {
        profiles.insertIfAbsent(created.getUserId(), AccountStatus.ACTIVE.name(), null, now);
        List<UserMedia> current = media.findByUserIdAndKindAndStatus(created.getUserId(), created.getKind(),
                MediaStatus.CURRENT);
        current.forEach(UserMedia::markPendingDelete);
        // Kısmi tekil indeks (kullanıcı, tür, CURRENT): eski satır önce PENDING_DELETE olarak yazılmalı.
        media.flush();
        media.save(created);
        media.flush();
        return current;
    }

    private void requireWithinUploadLimit(UUID userId, Instant now) {
        if (media.countUploadsSince(userId, now.minus(UPLOAD_WINDOW)) >= properties.uploadLimitPerHour()) {
            throw new UserException(UserErrorCode.MEDIA_RATE_LIMITED);
        }
    }

    private List<String> writeFiles(UserMedia created, ProcessedImage image) {
        List<String> written = new ArrayList<>();
        try {
            storage.write(created.largeFileName(), image.large());
            written.add(created.largeFileName());
            if (image.small() != null) {
                storage.write(created.smallFileName(), image.small());
                written.add(created.smallFileName());
            }
            return written;
        } catch (MediaStorageException _) {
            deleteQuietly(written);
            throw new UserException(UserErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        }
    }

    /** Commit sonrası eski dosyaları siler; başarısız olursa satır PENDING_DELETE kalır ve temizleyici yeniden dener. */
    private void deleteFiles(List<UserMedia> rows) {
        for (UserMedia row : rows) {
            try {
                MediaFiles.delete(storage, row);
            } catch (MediaStorageException _) {
                log.warn("Media file deletion deferred to cleanup: mediaId={}", row.getId());
            }
        }
    }

    private void deleteQuietly(List<String> fileNames) {
        for (String fileName : fileNames) {
            try {
                storage.delete(fileName);
            } catch (MediaStorageException _) {
                log.warn("Orphan media file could not be removed after a failed upload");
            }
        }
    }

    private static long size(ProcessedImage image) {
        return (long) image.large().length + (image.small() == null ? 0 : image.small().length);
    }

    private static String newKey() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
