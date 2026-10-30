package com.ltz.user.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.user.domain.enums.LinkedProvider;

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
 * Bir hesabın bir sağlayıcıdaki bağlantısı (USER-07/08). Satırı yalnız Auth olayları oluşturur ve değiştirir
 * (bkz. {@code LinkedAccountRepository}); kullanıcı yalnız {@code showOnProfile} iznini yönetir. Kaldırılan bağlantı
 * {@code active = false} satırı olarak kalır ve kimlik taşımaz.
 */
@Entity
@Table(name = "linked_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkedAccount {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 16)
    private LinkedProvider provider;

    @Column(name = "external_id", length = 32)
    private String externalId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "show_on_profile", nullable = false)
    private boolean showOnProfile;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Açık profilde gösterme iznini değiştirir; yalnız etkin bağlantıda anlamlıdır. İzin verilirken onay zamanı yazılır,
     * geri alınınca silinir.
     *
     * @return kayıt değiştiyse {@code true}
     */
    public boolean changeShowOnProfile(boolean show, Instant now) {
        if (!active || show == showOnProfile) {
            return false;
        }
        this.showOnProfile = show;
        this.consentAt = show ? now : null;
        return true;
    }

    /** Açık profilde gösterilecek mi: etkin bağlantı ve sahibin izni. */
    public boolean isPubliclyShown() {
        return active && showOnProfile;
    }
}
