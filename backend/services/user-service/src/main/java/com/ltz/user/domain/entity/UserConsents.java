package com.ltz.user.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kullanıcının açık ve geri alınabilir rızaları (LIB-14). Şimdilik tek rıza vardır: kişiselleştirilmiş öneri için
 * favori/bitirilen oyun verisinin işlenmesi. Satır yoksa rıza yoktur ({@link #defaults(UUID)}): varsayılan izin yok.
 */
@Entity
@Table(name = "user_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsents {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "recommendation_granted", nullable = false)
    private boolean recommendationGranted;

    @Column(name = "recommendation_changed_at", nullable = false)
    private Instant recommendationChangedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Kaydedilmemiş varsayılan: rıza yok, sürüm 0. */
    public static UserConsents defaults(UUID userId) {
        UserConsents consents = new UserConsents();
        consents.userId = userId;
        consents.version = 0L;
        return consents;
    }

    /** Kaydedilecek yeni satır: sürüm henüz atanmamış (JPA ilk yazmada 0 yapar). */
    public static UserConsents newRow(UUID userId, Instant now) {
        UserConsents consents = defaults(userId);
        consents.version = null;
        consents.recommendationChangedAt = now;
        consents.updatedAt = now;
        return consents;
    }

    /** @return rıza durumu değiştiyse {@code true} */
    public boolean applyRecommendation(boolean granted, Instant now) {
        boolean changed = granted != recommendationGranted;
        if (changed) {
            recommendationGranted = granted;
            recommendationChangedAt = now;
            updatedAt = now;
        }
        return changed;
    }
}
