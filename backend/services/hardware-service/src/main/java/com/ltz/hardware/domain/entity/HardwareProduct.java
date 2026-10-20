package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.ComponentCategory;
import jakarta.persistence.*;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Objects;
import java.time.Instant;
import com.ltz.hardware.domain.enums.CatalogStatus;
import com.ltz.hardware.domain.exception.HardwareConflictException;
import lombok.Getter;

/** A general model exists independently of its optional manufacturers. */
@Entity
@Table(name = "hardware_products")
@Getter
public class HardwareProduct {
    @Id private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ComponentCategory category;
    @Column(nullable = false, length = 160)
    private String model;
    @Column(name = "normalized_model", nullable = false, length = 160)
    private String normalizedModel;
    @Embedded private HardwareSpecifications specifications;
    @Version private Long version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CatalogStatus status;
    @ManyToMany
    @JoinTable(name = "hardware_product_brands", joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "brand_id"))
    private Set<HardwareBrand> brands = new HashSet<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected HardwareProduct() { }

    public HardwareProduct(ComponentCategory category, String model, HardwareSpecifications specifications,
                           Set<HardwareBrand> brands) {
        id = UUID.randomUUID();
        this.category = Objects.requireNonNull(category);
        status = CatalogStatus.ACTIVE;
        createdAt = Instant.now();
        update(model, specifications, brands);
    }

    public Set<HardwareBrand> getBrands() { return Collections.unmodifiableSet(brands); }

    public void requireVersion(long expected) {
        if (version == null || version != expected) throw new HardwareConflictException();
    }

    public void requireActive() {
        if (status != CatalogStatus.ACTIVE) throw new HardwareConflictException();
    }

    public void update(String model, HardwareSpecifications specifications, Set<HardwareBrand> brands) {
        requireActive();
        if (brands == null || brands.size() > 100) throw new IllegalArgumentException("Geçersiz marka seçenekleri");
        brands.forEach(HardwareBrand::requireActive);
        this.model = CatalogText.clean(model, 160);
        normalizedModel = CatalogText.normalize(model, 160);
        this.specifications = specifications;
        this.brands.clear();
        this.brands.addAll(brands);
        touch();
    }

    public void archive() {
        requireActive();
        status = CatalogStatus.ARCHIVED;
        touch();
    }

    private void touch() {
        Instant now = Instant.now();
        updatedAt = updatedAt != null && !now.isAfter(updatedAt) ? updatedAt.plusNanos(1000) : now;
    }
}
