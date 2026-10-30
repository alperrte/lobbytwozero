package com.ltz.user.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.user.domain.enums.GuestAccess;
import com.ltz.user.domain.enums.PrivacyField;
import com.ltz.user.domain.enums.Visibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Alan bazlı gizlilik ayarları (USER-06). Satır yoksa {@link #defaults(UUID)} geçerlidir (K4): arkadaş listesi
 * {@code FRIENDS}, kütüphane {@code PUBLIC}, donanım {@code FRIENDS}, performans {@code PRIVATE}, misafir {@code BASIC}.
 */
@Entity
@Table(name = "privacy_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivacySettings {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "friends_visibility", nullable = false, length = 16)
    private Visibility friendsList;

    @Enumerated(EnumType.STRING)
    @Column(name = "library_visibility", nullable = false, length = 16)
    private Visibility library;

    @Enumerated(EnumType.STRING)
    @Column(name = "hardware_visibility", nullable = false, length = 16)
    private Visibility hardware;

    @Enumerated(EnumType.STRING)
    @Column(name = "performance_visibility", nullable = false, length = 16)
    private Visibility performance;

    @Enumerated(EnumType.STRING)
    @Column(name = "guest_profile_access", nullable = false, length = 16)
    private GuestAccess guestAccess;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Kaydedilmemiş varsayılan ayarlar (sürüm 0). */
    public static PrivacySettings defaults(UUID userId) {
        PrivacySettings settings = new PrivacySettings();
        settings.userId = userId;
        settings.friendsList = Visibility.FRIENDS;
        settings.library = Visibility.PUBLIC;
        settings.hardware = Visibility.FRIENDS;
        settings.performance = Visibility.PRIVATE;
        settings.guestAccess = GuestAccess.BASIC;
        settings.version = 0L;
        return settings;
    }

    /** Kaydedilecek yeni satır: varsayılan değerler, sürüm henüz atanmamış (JPA ilk yazmada 0 yapar). */
    public static PrivacySettings newRow(UUID userId) {
        PrivacySettings settings = defaults(userId);
        settings.version = null;
        return settings;
    }

    public Visibility visibilityOf(PrivacyField field) {
        return switch (field) {
            case FRIENDS_LIST -> friendsList;
            case LIBRARY -> library;
            case HARDWARE -> hardware;
            case PERFORMANCE -> performance;
        };
    }

    /** @return kayıt değiştiyse {@code true} */
    public boolean apply(Visibility friendsList, Visibility library, Visibility hardware, Visibility performance,
                         GuestAccess guestAccess, Instant now) {
        boolean changed = this.friendsList != friendsList || this.library != library || this.hardware != hardware
                || this.performance != performance || this.guestAccess != guestAccess;
        if (changed || updatedAt == null) {
            this.friendsList = friendsList;
            this.library = library;
            this.hardware = hardware;
            this.performance = performance;
            this.guestAccess = guestAccess;
            this.updatedAt = now;
        }
        return changed;
    }
}
