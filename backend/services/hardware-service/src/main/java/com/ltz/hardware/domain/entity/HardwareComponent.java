package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.ComponentCategory;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "hardware_profile_components")
@Getter
public class HardwareComponent {
    @Id private UUID id;
    @Column(name = "profile_id", nullable = false, updatable = false) private UUID profileId;
    @Column(name = "product_id") private UUID productId;
    @Column(name = "brand_id") private UUID brandId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private ComponentCategory category;
    @Column(name = "model_name", nullable = false, length = 160) private String modelName;
    @Column(name = "brand_name", length = 100) private String brandName;
    @Column(nullable = false) private int quantity;
    @Embedded private HardwareSpecifications specifications;
    @Column(nullable = false, length = 32) private String source;
    @Column(name = "sync_component_key") private UUID syncComponentKey;
    @Version private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected HardwareComponent() { }

    public record Selection(ComponentCategory category, UUID productId, UUID brandId,
                            String modelName, String brandName, HardwareSpecifications specifications) { }

    public HardwareComponent(UUID profileId, Selection selection, int quantity) {
        this.id = UUID.randomUUID();
        this.profileId = Objects.requireNonNull(profileId);
        this.category = Objects.requireNonNull(selection.category());
        this.productId = selection.productId();
        this.brandId = selection.brandId();
        this.modelName = name(selection.modelName(), 160, true);
        this.brandName = name(selection.brandName(), 100, false);
        if (quantity < 1 || quantity > 128 || (brandId != null && productId == null)) {
            throw new IllegalArgumentException("Geçersiz bileşen");
        }
        this.quantity = quantity;
        this.specifications = selection.specifications();
        this.source = "MANUAL";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    private static String name(String value, int max, boolean required) {
        if (!required && value == null) return null;
        if (value == null || value.isBlank() || value.length() > max
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>')) {
            throw new IllegalArgumentException("Geçersiz bileşen adı");
        }
        return value.strip();
    }

    public void replaceWith(HardwareComponent replacement) {
        copyDetails(replacement);
        source = "MANUAL";
        syncComponentKey = null;
    }

    public static HardwareComponent desktop(UUID profileId, HardwareScan.Component scanned) {
        var component = new HardwareComponent(profileId, scanned.selection(), scanned.quantity());
        component.source = "DESKTOP_SYNC";
        component.syncComponentKey = scanned.componentKey();
        return component;
    }

    public void replaceFromScan(HardwareScan.Component scanned) {
        if (!"DESKTOP_SYNC".equals(source) || !Objects.equals(syncComponentKey, scanned.componentKey())) {
            throw new IllegalArgumentException("Senkronizasyon anahtarı uyuşmuyor");
        }
        copyDetails(desktop(profileId, scanned));
    }

    private void copyDetails(HardwareComponent replacement) {
        if (!profileId.equals(replacement.profileId)) throw new IllegalArgumentException("Profil uyuşmuyor");
        this.category = replacement.category;
        this.productId = replacement.productId;
        this.brandId = replacement.brandId;
        this.modelName = replacement.modelName;
        this.brandName = replacement.brandName;
        this.quantity = replacement.quantity;
        this.specifications = replacement.specifications;
        var now = Instant.now();
        this.updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }
}
