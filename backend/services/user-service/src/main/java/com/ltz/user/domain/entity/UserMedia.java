package com.ltz.user.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.user.domain.enums.MediaKind;
import com.ltz.user.domain.enums.MediaStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Profil görselinin üst verisi. Dosya diskte, {@code storageKey} ile anılır: büyük sürüm {@code <key>.<uzantı>}, küçük
 * sürüm {@code <key>-s.<uzantı>} (GIF'te küçük sürüm yoktur). Kullanıcı başına her tür için tek {@code CURRENT} satır
 * olabilir (kısmi tekil indeks). Değiştirilen ya da kaldırılan görsel {@code PENDING_DELETE} olur; satır, yükleme sınırı
 * penceresi (bir saat) dolana kadar tutulur çünkü yükleme sayısı bu satırlardan hesaplanır.
 */
@Entity
@Table(name = "user_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMedia {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 16)
    private MediaKind kind;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 64)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 32)
    private String contentType;

    @Column(name = "byte_size", nullable = false, updatable = false)
    private long byteSize;

    @Column(name = "width", nullable = false, updatable = false)
    private int width;

    @Column(name = "height", nullable = false, updatable = false)
    private int height;

    @Column(name = "frame_count", nullable = false, updatable = false)
    private int frameCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MediaStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Yeni görselin dosya ve boyut üst verisi. */
    public record Upload(String storageKey, String contentType, long byteSize, int width, int height,
                         int frameCount) {
    }

    public static UserMedia create(UUID userId, MediaKind kind, Upload upload, Instant now) {
        UserMedia media = new UserMedia();
        media.id = UUID.randomUUID();
        media.userId = userId;
        media.kind = kind;
        media.storageKey = upload.storageKey();
        media.contentType = upload.contentType();
        media.byteSize = upload.byteSize();
        media.width = upload.width();
        media.height = upload.height();
        media.frameCount = upload.frameCount();
        media.status = MediaStatus.CURRENT;
        media.createdAt = now;
        return media;
    }

    public void markPendingDelete() {
        this.status = MediaStatus.PENDING_DELETE;
    }

    public boolean isAnimated() {
        return frameCount > 1;
    }

    /** Büyük sürümün dosya adı ({@code <anahtar>.<uzantı>}). */
    public String largeFileName() {
        return storageKey + "." + extension();
    }

    /** Küçük sürümün dosya adı; GIF'in küçük sürümü yoktur ({@code null}). */
    public String smallFileName() {
        return GIF.equals(contentType) ? null : storageKey + "-s." + extension();
    }

    private String extension() {
        return switch (contentType) {
            case "image/png" -> "png";
            case GIF -> "gif";
            default -> "jpg";
        };
    }

    private static final String GIF = "image/gif";
}
