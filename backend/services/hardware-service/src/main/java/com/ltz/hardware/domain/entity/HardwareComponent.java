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
    @Column(nullable = false, length = 32, updatable = false) private String source;
    @Version private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected HardwareComponent() { }

    public HardwareComponent(UUID profileId, ComponentCategory category, UUID productId, UUID brandId,
                             String modelName, String brandName, int quantity, HardwareSpecifications specifications) {
        this.id = UUID.randomUUID();
        this.profileId = Objects.requireNonNull(profileId);
        this.category = Objects.requireNonNull(category);
        this.productId = productId;
        this.brandId = brandId;
        this.modelName = name(modelName, 160, true);
        this.brandName = name(brandName, 100, false);
        if (quantity < 1 || quantity > 128 || (brandId != null && productId == null)) {
            throw new IllegalArgumentException("Geçersiz bileşen");
        }
        this.quantity = quantity;
        this.specifications = specifications;
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
        if (!profileId.equals(replacement.profileId)) throw new IllegalArgumentException("Profil uyuşmuyor");
        this.category = replacement.category;
        this.productId = replacement.productId;
        this.brandId = replacement.brandId;
        this.modelName = replacement.modelName;
        this.brandName = replacement.brandName;
        this.quantity = replacement.quantity;
        this.specifications = replacement.specifications;
        this.updatedAt = Instant.now();
    }
}
