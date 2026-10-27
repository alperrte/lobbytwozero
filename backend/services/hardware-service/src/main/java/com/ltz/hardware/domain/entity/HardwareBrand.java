package com.ltz.hardware.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;
import com.ltz.hardware.domain.enums.CatalogStatus;
import com.ltz.hardware.domain.exception.HardwareConflictException;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.time.Instant;

@Entity
@Table(name = "hardware_brands")
@Getter
public class HardwareBrand {
    @Id private UUID id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;
    @Version private Long version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CatalogStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected HardwareBrand() { }

    public HardwareBrand(String name) {
        id = UUID.randomUUID();
        status = CatalogStatus.ACTIVE;
        createdAt = Instant.now();
        rename(name);
    }

    public void requireVersion(long expected) {
        if (version == null || version != expected) throw new HardwareConflictException();
    }

    public void requireActive() {
        if (status != CatalogStatus.ACTIVE) throw new HardwareConflictException();
    }

    public void rename(String name) {
        requireActive();
        this.name = CatalogText.clean(name, 100);
        normalizedName = CatalogText.normalize(name, 100);
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
