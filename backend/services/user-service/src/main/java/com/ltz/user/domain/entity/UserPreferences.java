package com.ltz.user.domain.entity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.ltz.user.domain.enums.Theme;

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
 * Kullanıcı tercihleri (USER-10): arayüz dili (tr/en), tema ve iki sınırlı arayüz tercihi. Satır yoksa
 * {@link #defaults(UUID)} geçerlidir: dil {@code tr}, tema {@code SYSTEM}, hareket azaltma ve sıkı yerleşim kapalı.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferences {

    /** Desteklenen arayüz dilleri (DB'deki CHECK ile aynı). */
    public static final Set<String> LOCALES = Set.of("tr", "en");

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 16)
    private Theme theme;

    @Column(name = "reduced_motion", nullable = false)
    private boolean reducedMotion;

    @Column(name = "compact_layout", nullable = false)
    private boolean compactLayout;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Kaydedilmemiş varsayılan tercihler (sürüm 0). */
    public static UserPreferences defaults(UUID userId) {
        UserPreferences preferences = new UserPreferences();
        preferences.userId = userId;
        preferences.locale = "tr";
        preferences.theme = Theme.SYSTEM;
        preferences.version = 0L;
        return preferences;
    }

    /** Kaydedilecek yeni satır: varsayılan değerler, sürüm henüz atanmamış (JPA ilk yazmada 0 yapar). */
    public static UserPreferences newRow(UUID userId) {
        UserPreferences preferences = defaults(userId);
        preferences.version = null;
        return preferences;
    }

    /** @return kayıt değiştiyse {@code true} */
    public boolean apply(String locale, Theme theme, boolean reducedMotion, boolean compactLayout, Instant now) {
        boolean changed = !locale.equals(this.locale) || theme != this.theme || reducedMotion != this.reducedMotion
                || compactLayout != this.compactLayout;
        if (changed || updatedAt == null) {
            this.locale = locale;
            this.theme = theme;
            this.reducedMotion = reducedMotion;
            this.compactLayout = compactLayout;
            this.updatedAt = now;
        }
        return changed;
    }
}
