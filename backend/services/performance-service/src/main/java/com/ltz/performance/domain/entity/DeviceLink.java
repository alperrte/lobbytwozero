package com.ltz.performance.domain.entity;

import com.ltz.performance.domain.enums.DeviceLinkStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Auth Service'teki kanonik cihaza Performance tarafındaki referans. Kimlik, token ve iptal Auth'tadır;
 * burada yalnız cihazın bu servisle ilişkisi, sahibi ve senkronizasyona açık olup olmadığı tutulur.
 */
@Entity
@Table(name = "device_links")
@Getter
public class DeviceLink {
    public static final int LABEL_MAX = 64;
    private static final String DEFAULT_LABEL = "Cihaz";

    @Id private UUID id;
    @Column(name = "auth_device_id", nullable = false, updatable = false) private UUID authDeviceId;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(nullable = false, length = LABEL_MAX) private String label;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private DeviceLinkStatus status;
    @Column(name = "linked_at", nullable = false) private Instant linkedAt;
    @Column(name = "last_sync_at") private Instant lastSyncAt;
    @Column(name = "disabled_at") private Instant disabledAt;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected DeviceLink() { }

    public static DeviceLink create(UUID authDeviceId, UUID userId, String label, Instant now) {
        var link = new DeviceLink();
        link.id = UUID.randomUUID();
        link.authDeviceId = Objects.requireNonNull(authDeviceId);
        link.userId = Objects.requireNonNull(userId);
        link.createdAt = now;
        link.activate(label, now);
        return link;
    }

    /** Etiket Auth'tan gelir; kontrol karakterleri atılır ve sınırlandırılır. */
    public static String normalizeLabel(String raw) {
        if (raw == null) return DEFAULT_LABEL;
        var cleaned = raw.replaceAll("\\p{Cntrl}", " ").strip();
        if (cleaned.isEmpty()) return DEFAULT_LABEL;
        return cleaned.codePointCount(0, cleaned.length()) <= LABEL_MAX ? cleaned
                : cleaned.substring(0, cleaned.offsetByCodePoints(0, LABEL_MAX));
    }

    public void activate(String label, Instant now) {
        this.label = normalizeLabel(label);
        status = DeviceLinkStatus.ACTIVE;
        linkedAt = now;
        disabledAt = null;
        updatedAt = now;
    }

    /** İdempotent: kayıt silinmez, DISABLED olarak kalır; tekrar bağlama aynı kaydı etkinleştirir. */
    public void disable(Instant now) {
        if (!isActive()) return;
        status = DeviceLinkStatus.DISABLED;
        disabledAt = now;
        updatedAt = now;
    }

    public void recordSync(Instant now) {
        lastSyncAt = now;
        updatedAt = now;
    }

    public boolean isActive() { return status == DeviceLinkStatus.ACTIVE; }

    public boolean isOwnedBy(UUID owner) { return userId.equals(owner); }
}
